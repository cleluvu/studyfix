package vn.studyfix.client.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** One persistent framed-TCP connection. Commands are queued in order; responses are asynchronous. */
public final class TcpControlConnection implements AutoCloseable {
    private static final int OUTBOUND_CAPACITY = 256;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ArrayBlockingQueue<String> outbound = new ArrayBlockingQueue<>(OUTBOUND_CAPACITY);
    private final CopyOnWriteArrayList<ConnectionListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Waiter> waiters = new CopyOnWriteArrayList<>();
    private final HistoryFrameAssembler assembler = new HistoryFrameAssembler();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "studyfix-heartbeat"));
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Throwable closeCause;

    private TcpControlConnection(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        Thread reader = daemon(this::readLoop, "studyfix-tcp-reader");
        Thread writer = daemon(this::writeLoop, "studyfix-tcp-writer");
        reader.start(); writer.start();
        heartbeat.scheduleAtFixedRate(() -> {
            if (!closed.get()) {
                try { send("/ping"); } catch (IOException e) { fail(e); }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public static TcpControlConnection connect(String host, int port, int timeoutMillis) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        socket.setKeepAlive(true);
        try { return new TcpControlConnection(socket); }
        catch (IOException e) { try { socket.close(); } catch (IOException ignored) { } throw e; }
    }
    private static Thread daemon(Runnable task, String name) { Thread t = new Thread(task, name); t.setDaemon(true); return t; }
    public boolean isConnected() { return !closed.get() && socket.isConnected() && !socket.isClosed(); }
    public String remoteHost() { return socket.getInetAddress().getHostAddress(); }
    public int remotePort() { return socket.getPort(); }
    public void addListener(ConnectionListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    public void removeListener(ConnectionListener listener) { listeners.remove(listener); }

    public void send(String message) throws IOException {
        Objects.requireNonNull(message, "message");
        if (message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > FrameCodec.MAX_FRAME_BYTES)
            throw new IOException("Lệnh vượt giới hạn frame 64 KiB");
        if (!isConnected()) throw new IOException("Kết nối StudyFix đã đóng");
        if (!outbound.offer(message)) throw new IOException("Hàng đợi gửi đầy; cần kết nối lại để đồng bộ trạng thái");
    }

    public String sendAndAwait(String command, Predicate<String> responseMatcher, Duration timeout)
            throws IOException, TimeoutException, InterruptedException {
        Waiter waiter = new Waiter(responseMatcher);
        waiters.add(waiter);
        try {
            send(command);
            return waiter.future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("Không nhận được phản hồi hợp lệ", cause);
        } finally { waiters.remove(waiter); }
    }

    private void readLoop() {
        try {
            while (!closed.get()) {
                String frame = FrameCodec.read(in);
                var logical = assembler.accept(frame);
                if (logical.isEmpty()) continue;
                String message = logical.get();
                for (Waiter waiter : waiters) {
                    try { if (waiter.matcher.test(message)) waiter.future.complete(message); }
                    catch (RuntimeException ignored) { }
                }
                for (ConnectionListener listener : listeners) {
                    try { listener.onMessage(message); } catch (RuntimeException ignored) { }
                }
            }
        } catch (IOException e) { fail(e); }
    }
    private void writeLoop() {
        try {
            while (!closed.get()) {
                String message = outbound.take();
                FrameCodec.write(out, message);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException e) { fail(e); }
    }
    private void fail(Throwable cause) {
        if (!closed.compareAndSet(false, true)) return;
        closeCause = cause;
        heartbeat.shutdownNow();
        try { socket.close(); } catch (IOException ignored) { }
        IOException pendingFailure = new IOException("Kết nối đã đóng", cause);
        for (Waiter waiter : waiters) waiter.future.completeExceptionally(pendingFailure);
        for (ConnectionListener listener : listeners) {
            try { listener.onDisconnected(cause); } catch (RuntimeException ignored) { }
        }
    }
    public Throwable closeCause() { return closeCause; }
    @Override public void close() {
        if (closed.get()) return;
        try { send("/quit"); } catch (IOException ignored) { }
        fail(new IOException("Client đóng kết nối"));
    }
    private static final class Waiter {
        private final Predicate<String> matcher;
        private final CompletableFuture<String> future = new CompletableFuture<>();
        private Waiter(Predicate<String> matcher) { this.matcher = matcher; }
    }
}
