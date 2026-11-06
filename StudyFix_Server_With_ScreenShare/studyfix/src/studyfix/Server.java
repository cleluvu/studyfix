package studyfix;

import studyfix.screenshare.ScreenShareRelay;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Server implements AutoCloseable {
    final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    final ScheduledThreadPoolExecutor monitor = new ScheduledThreadPoolExecutor(1);
    private final ExecutorService workers = new ThreadPoolExecutor(0, 256, 30,
            TimeUnit.SECONDS, new SynchronousQueue<>());
    private final Store store;
    private final ServerSocket listener;
    /** TCP relay ảnh màn hình, tách khỏi kênh điều khiển. */
    private final ScreenShareRelay screenShare;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Deque<String> activity = new ArrayDeque<>();
    private final CopyOnWriteArrayList<StudyFixGateway.DepartureListener> departureListeners = new CopyOnWriteArrayList<>();
    private Thread acceptThread;
    public Server(int port, Path database) throws Exception {
        monitor.setRemoveOnCancelPolicy(true);
        store = new Store(database);
        ServerSocket candidateListener = null;
        ScreenShareRelay candidateScreenShare = null;
        try {
            for (Store.StoredRoom row : store.loadRooms()) {
                rooms.put(row.id(), new Room(this, row));
            }
            // Cổng TCP điều khiển hiện tại (mặc định 5000).
            candidateListener = new ServerSocket(port);
            // Cổng truyền ảnh riêng (mặc định 5001); có thể dùng 0 để lấy cổng trống khi kiểm thử.
            int screenPort = Integer.getInteger("studyfix.screen.port", 5001);
            candidateScreenShare = new ScreenShareRelay(gateway(), screenPort);
        } catch (Exception e) {
            if (candidateScreenShare != null) candidateScreenShare.close();
            if (candidateListener != null) {
                try { candidateListener.close(); } catch (IOException ignored) { }
            }
            try { store.close(); } catch (java.sql.SQLException closeError) { e.addSuppressed(closeError); }
            monitor.shutdownNow();
            workers.shutdownNow();
            throw e;
        }
        this.listener = candidateListener;
        this.screenShare = candidateScreenShare;
        log("Database: " + database.toAbsolutePath());
        log("Screen relay chạy ở cổng " + screenShare.port());
    }
    public synchronized void start() {
        if (acceptThread != null || closed.get()) throw new IllegalStateException("Server đã chạy hoặc đã dừng");
        acceptThread = new Thread(this::acceptLoop, "studyfix-listener");
        acceptThread.start();
        log("StudyFix Server chạy ở cổng " + port());
    }
    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket socket = listener.accept();
                if (closed.get() || clients.size() >= 128) { socket.close(); continue; }
                try {
                    ClientHandler client = new ClientHandler(this, socket);
                    clients.put(client.getMemberId(), client);
                    try { workers.execute(client); }
                    catch (RejectedExecutionException e) { clients.remove(client.getMemberId()); client.closeConnection(); }
                } catch (IOException e) { socket.close(); log("Không tạo được phiên: " + e.getMessage()); }
            } catch (IOException e) { if (!closed.get()) log("Lỗi listener: " + e.getMessage()); }
        }
    }
    void executeWriter(Runnable task) { workers.execute(task); }
    Store store() { return store; }
    Room createRoom(Identity owner) throws java.sql.SQLException {
        if (owner.role() != Identity.Role.TEACHER) throw new SecurityException("Chỉ giáo viên được tạo phòng.");
        Room room = new Room(this, store.createRoom(owner, Room.INITIAL_CODE));
        rooms.put(room.getRoomId(), room); log("Tạo phòng " + room.getRoomId() + " owner=" + owner.userId()); return room;
    }
    public int port() { return listener.getLocalPort(); }
    public int screenSharePort() { return screenShare.port(); }
    public boolean isRunning() { return acceptThread != null && !closed.get(); }
    public void log(String message) {
        String row = Instant.now() + " " + message;
        synchronized (activity) {
            if (activity.size() == 500) activity.removeFirst();
            activity.addLast(row);
        }
        System.out.println(row);
    }
    public record Snapshot(boolean running, int port, int connections, List<Room.Snapshot> rooms, List<String> log) { }
    public Snapshot snapshot() {
        List<Room.Snapshot> list = rooms.values().stream().map(Room::snapshot)
                .sorted(Comparator.comparing(Room.Snapshot::roomId)).toList();
        List<String> logs; synchronized (activity) { logs = List.copyOf(activity); }
        return new Snapshot(isRunning(), port(), clients.size(), list, logs);
    }
    public StudyFixGateway gateway() { return new StudyFixGateway(this); }
    /** Relay used by authenticated handlers to issue one-time screen-share tickets. */
    public ScreenShareRelay screenShare() { return screenShare; }
    void addDepartureListener(StudyFixGateway.DepartureListener listener) { departureListeners.add(listener); }
    void removeDepartureListener(StudyFixGateway.DepartureListener listener) { departureListeners.remove(listener); }
    void departed(String session, Identity identity, String room, String reason) {
        log("Rời phòng " + room + " session=" + session + " lý do=" + reason);
        for (var listener : departureListeners) {
            try { listener.departed(session, identity, room, reason); }
            catch (RuntimeException e) { log("Lỗi callback rời phòng: " + e); }
        }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        // Stop image sockets first so no stream keeps running while the control server shuts down.
        screenShare.close();
        try { listener.close(); } catch (IOException ignored) { }
        try { if (acceptThread != null) acceptThread.join(5000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        clients.values().forEach(ClientHandler::closeConnection);
        monitor.shutdownNow(); workers.shutdownNow();
        try {
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) log("Còn callback đang kết thúc");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { store.close(); } catch (java.sql.SQLException e) { log("Lỗi đóng database: " + e.getMessage()); }
        log("Server đã dừng");
    }
    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--admin")) { ServerAdmin.main(args); return; }
        Server server = new Server(Integer.getInteger("studyfix.port", 5000),
                Path.of(System.getProperty("studyfix.db", "data/studyfix.db")));
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
    }
}
