package studyfix.screenshare;

import studyfix.StudyFixGateway;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relay nhận ảnh màn hình từ người chia sẻ rồi chuyển đến các thành viên đang xem.
 * Dùng cổng TCP riêng, mặc định 5001; người dùng cần vé hợp lệ để kết nối.
 */
public final class ScreenShareRelay implements AutoCloseable {
    public enum Mode { PUBLISH, VIEW }
    private static final long TICKET_LIFETIME_NANOS = Duration.ofSeconds(30).toNanos();
    private static final long MIN_FRAME_INTERVAL_NANOS = Duration.ofMillis(100).toNanos(); // Tối đa 10 khung hình mỗi giây.
    private static final int MAX_PENDING_TICKETS = 4096;
    private final StudyFixGateway gateway;
    private final ServerSocket listener;
    private final ExecutorService workers = new ThreadPoolExecutor(0, 64, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "studyfix-screen-relay-worker"); t.setDaemon(true); return t; });
    private final Thread acceptThread;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Peer> publishers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Peer>> viewers = new ConcurrentHashMap<>();
    private final Set<Peer> peers = ConcurrentHashMap.newKeySet();
    private final StudyFixGateway.DepartureListener departures = (session, identity, room, reason) -> {
        for (Peer peer : peers) if (peer.sessionId.equals(session)) peer.closeSocket();
    };

    public ScreenShareRelay(StudyFixGateway gateway, int port) throws IOException {
        this.gateway = gateway;
        this.listener = new ServerSocket(port);
        gateway.addDepartureListener(departures);
        acceptThread = new Thread(this::acceptLoop, "studyfix-screen-relay-accept");
        acceptThread.setDaemon(true); acceptThread.start();
    }
    public int port() { return listener.getLocalPort(); }

    /** ClientHandler chỉ gọi sau khi xác thực và kiểm tra phòng hiện tại. Vé dùng một lần, có hạn 30 giây. */
    public String issueTicket(String sessionId, String roomId, Mode mode) {
        if (!gateway.isMember(sessionId, roomId)) throw new SecurityException("Session is not a member of room");
        long now = System.nanoTime();
        tickets.entrySet().removeIf(entry -> now - entry.getValue().expiresAtNanos >= 0);
        if (tickets.size() >= MAX_PENDING_TICKETS) throw new IllegalStateException("Too many pending screen tickets");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(value, new Ticket(sessionId, roomId, mode, System.nanoTime() + TICKET_LIFETIME_NANOS));
        return value;
    }
    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket socket = listener.accept(); socket.setTcpNoDelay(true); socket.setKeepAlive(true); socket.setSoTimeout(5000);
                if (peers.size() >= 64) { socket.close(); continue; }
                try { workers.execute(() -> handleSocket(socket)); }
                catch (RejectedExecutionException rejected) { try { socket.close(); } catch (IOException ignored) { } }
            } catch (IOException e) { if (!closed.get()) System.err.println("Screen relay accept: " + e.getMessage()); }
            catch (RuntimeException e) { if (!closed.get()) System.err.println("Screen relay worker error: " + e.getMessage()); }
        }
    }
    private void handleSocket(Socket socket) {
        Peer peer = null;
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ScreenShareWire.Packet hello = ScreenShareWire.read(in);
            if (hello.type() != ScreenShareWire.Type.HELLO) throw new IOException("First packet must be HELLO");
            String token = ScreenShareWire.fromUtf8(hello.payload()).trim();
            Ticket ticket = tickets.remove(token); // Token dùng một lần giúp tránh giả mạo bằng mã phiên.
            if (ticket == null || System.nanoTime() > ticket.expiresAtNanos) throw new SecurityException("Ticket missing or expired");
            if (!gateway.isMember(ticket.sessionId, ticket.roomId)) throw new SecurityException("Session left the room");
            socket.setSoTimeout(0);
            peer = new Peer(socket, in, out, ticket);
            peers.add(peer);
            if (ticket.mode == Mode.PUBLISH) {
                Peer previous = publishers.putIfAbsent(ticket.roomId, peer);
                if (previous != null) throw new IllegalStateException("A publisher is already active in this room");
            } else {
                viewers.computeIfAbsent(ticket.roomId, ignored -> ConcurrentHashMap.newKeySet()).add(peer);
            }
            ScreenShareWire.write(out, ScreenShareWire.Type.STATUS, ScreenShareWire.utf8("OK " + ticket.mode + " room=" + ticket.roomId));
            if (ticket.mode == Mode.VIEW) peer.startViewerSender();
            if (ticket.mode == Mode.PUBLISH) publishControlEvent(peer, "STARTED");
            while (!closed.get() && !peer.closed.get()) {
                ScreenShareWire.Packet packet = ScreenShareWire.read(in);
                if (packet.type() == ScreenShareWire.Type.STOP) break;
                if (packet.type() != ScreenShareWire.Type.FRAME || ticket.mode != Mode.PUBLISH)
                    throw new IOException("Only a publisher may send FRAME packets");
                byte[] jpeg = packet.payload();
                if (jpeg.length < 4 || jpeg.length > ScreenShareWire.MAX_PACKET_BYTES - 1
                        || (jpeg[0] & 0xff) != 0xff || (jpeg[1] & 0xff) != 0xd8)
                    throw new IOException("Invalid JPEG payload size/signature");
                if (!gateway.isMember(ticket.sessionId, ticket.roomId) || publishers.get(ticket.roomId) != peer)
                    throw new SecurityException("Publisher is no longer authorized");
                if (!peer.acceptFrameTime()) continue;
                Set<Peer> destinations = viewers.getOrDefault(ticket.roomId, Set.of());
                for (Peer viewer : destinations) {
                    if (gateway.isMember(viewer.sessionId, viewer.roomId)) viewer.offerFrame(jpeg);
                    else viewer.closeSocket();
                }
            }
        } catch (Exception e) {
            try { ScreenShareWire.write(new DataOutputStream(socket.getOutputStream()), ScreenShareWire.Type.ERROR, ScreenShareWire.utf8(e.getMessage() == null ? "Rejected" : e.getMessage())); } catch (IOException ignored) { }
        } finally {
            if (peer != null) peer.closeSocket();
            else try { socket.close(); } catch (IOException ignored) { }
        }
    }
    private void publishControlEvent(Peer peer, String event) {
        try { gateway.sendToRoom(peer.sessionId, peer.roomId, "[SCREEN_SHARE] " + event + " room=" + peer.roomId + " session=" + peer.sessionId); }
        catch (Exception ignored) { }
    }
    private void onPeerClosed(Peer peer) {
        peers.remove(peer);
        if (peer.mode == Mode.PUBLISH) {
            if (publishers.remove(peer.roomId, peer)) {
                for (Peer viewer : viewers.getOrDefault(peer.roomId, Set.of())) {
                    try { viewer.send(ScreenShareWire.Type.STOP, new byte[0]); } catch (IOException ignored) { }
                    viewer.closeSocket();
                }
                publishControlEvent(peer, "STOPPED");
            }
        } else {
            Set<Peer> roomViewers = viewers.get(peer.roomId);
            if (roomViewers != null) { roomViewers.remove(peer); if (roomViewers.isEmpty()) viewers.remove(peer.roomId, roomViewers); }
        }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        gateway.removeDepartureListener(departures);
        try { listener.close(); } catch (IOException ignored) { }
        for (Peer peer : peers) peer.closeSocket();
        workers.shutdownNow();
        try { acceptThread.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        tickets.clear(); publishers.clear(); viewers.clear();
    }
    private record Ticket(String sessionId, String roomId, Mode mode, long expiresAtNanos) { }
    private final class Peer {
        final Socket socket; final DataInputStream in; final DataOutputStream out; final String sessionId; final String roomId; final Mode mode;
        final AtomicBoolean closed = new AtomicBoolean();
        final BlockingQueue<byte[]> latestFrames = new ArrayBlockingQueue<>(1);
        volatile Thread frameWriter;
        volatile long lastFrameNanos;
        Peer(Socket socket, DataInputStream in, DataOutputStream out, Ticket ticket) {
            this.socket = socket; this.in = in; this.out = out; this.sessionId = ticket.sessionId; this.roomId = ticket.roomId; this.mode = ticket.mode;
        }
        void send(ScreenShareWire.Type type, byte[] payload) throws IOException { if (!closed.get()) ScreenShareWire.write(out, type, payload); }
        boolean acceptFrameTime() {
            long now = System.nanoTime();
            if (lastFrameNanos != 0 && now - lastFrameNanos < MIN_FRAME_INTERVAL_NANOS) return false;
            lastFrameNanos = now; return true;
        }
        void offerFrame(byte[] jpeg) {
            if (closed.get() || mode != Mode.VIEW) return;
            if (!latestFrames.offer(jpeg)) { latestFrames.poll(); latestFrames.offer(jpeg); }
        }
        void startViewerSender() {
            if (mode != Mode.VIEW) return;
            frameWriter = new Thread(() -> {
                try {
                    while (!closed.get()) send(ScreenShareWire.Type.FRAME, latestFrames.take());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (IOException e) { closeSocket(); }
            }, "studyfix-screen-viewer-writer");
            frameWriter.setDaemon(true); frameWriter.start();
        }
        void closeSocket() {
            if (!closed.compareAndSet(false, true)) return;
            Thread writer = frameWriter; if (writer != null) writer.interrupt();
            try { socket.close(); } catch (IOException ignored) { }
            onPeerClosed(this);
        }
    }
}
