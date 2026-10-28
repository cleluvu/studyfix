package studyfix;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * TCP control-channel session for GUI (and reusable by console).
 * Heartbeat, HISTORY_PART reassembly, and send mirror {@link Client}.
 */
public final class ClientSession implements AutoCloseable {
    public interface Listener {
        void onMessage(String text);
        void onDisconnected(String reason);
    }

    private final Socket socket = new Socket();
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean quitting = new AtomicBoolean();
    private final AtomicLong lastPong = new AtomicLong(System.nanoTime());
    private final AtomicReference<String> endedReason = new AtomicReference<>();
    private final ScheduledExecutorService heartbeat;
    private final Thread reader;
    private final HistoryBuffer history = new HistoryBuffer();

    private ClientSession(String host, int port, String username, String password, Listener listener)
            throws IOException {
        this.listener = Objects.requireNonNull(listener);
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        socket.setSoTimeout(5000);
        Protocol.send(out, "/login " + username + " " + password);
        String greeting = Protocol.receive(in);
        listener.onMessage(greeting);
        if (!greeting.startsWith("[LOGIN_OK]")) {
            socket.close();
            throw new IOException(greeting.startsWith("[LOGIN_FAILED]")
                    ? greeting
                    : "Đăng nhập thất bại.");
        }
        socket.setSoTimeout(0);

        heartbeat = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "gui-client-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            if (socket.isClosed()) return;
            try {
                Protocol.send(out, "/ping");
            } catch (IOException e) {
                quietClose();
            }
        }, 0, 5, TimeUnit.SECONDS);
        heartbeat.scheduleAtFixedRate(() -> {
            if (System.nanoTime() - lastPong.get() >= TimeUnit.SECONDS.toNanos(20)) {
                endedReason.compareAndSet(null, "Server không phản hồi pong trong 20 giây.");
                quietClose();
            }
        }, 1, 1, TimeUnit.SECONDS);

        reader = new Thread(this::readLoop, "gui-client-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public static ClientSession connect(String host, int port, String username, String password,
                                        Listener listener) throws IOException {
        return new ClientSession(host, port, username, password, listener);
    }

    public void send(String message) throws IOException {
        if (closed.get()) throw new IOException("Đã ngắt kết nối.");
        if (message.getBytes(StandardCharsets.UTF_8).length > Protocol.MAX_BYTES) {
            throw new IOException("Nội dung quá dài (tối đa 64 KiB frame).");
        }
        Protocol.send(out, message);
    }

    public void disconnect() {
        quitting.set(true);
        quietClose();
    }

    @Override
    public void close() {
        disconnect();
    }

    private void quietClose() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        heartbeat.shutdownNow();
    }

    private void readLoop() {
        try {
            while (!closed.get()) {
                String response = Protocol.receive(in);
                if (response.equals("/pong")) {
                    lastPong.set(System.nanoTime());
                    continue;
                }
                if (response.startsWith("[HISTORY_PART ")) {
                    history.part(response, listener::onMessage);
                } else {
                    history.deliver(response, listener::onMessage);
                }
            }
        } catch (IOException e) {
            if (!quitting.get()) {
                String reason = endedReason.get();
                if (reason == null) {
                    reason = e instanceof EOFException
                            ? "Server đã đóng kết nối."
                            : "Mất kết nối với server (" + e.getClass().getSimpleName() + ").";
                }
                history.finishIncomplete(listener::onMessage);
                listener.onDisconnected(reason);
            } else {
                history.finishIncomplete(listener::onMessage);
                listener.onDisconnected("Đã ngắt kết nối.");
            }
        } finally {
            quietClose();
        }
    }

    /** Reassemble multi-frame history; hold other lines until the last part. */
    private static final class HistoryBuffer {
        private final Deque<String> pending = new ArrayDeque<>();
        private final StringBuilder body = new StringBuilder();
        private int nextPart;
        private int totalParts;

        synchronized void deliver(String text, Consumer<String> emit) {
            if (nextPart != 0) pending.addLast(text);
            else emit.accept(text);
        }

        synchronized void part(String frame, Consumer<String> emit) throws IOException {
            int end = frame.indexOf("]\n");
            int index;
            int count;
            try {
                if (end < 0) throw new IllegalArgumentException();
                String[] position = frame.substring(14, end).split("/", -1);
                if (position.length != 2) throw new IllegalArgumentException();
                index = Integer.parseInt(position[0]);
                count = Integer.parseInt(position[1]);
                if (index < 1 || count < 1 || index > count
                        || (nextPart == 0 ? index != 1 : index != nextPart || count != totalParts)) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Thứ tự các phần lịch sử không hợp lệ.", e);
            }
            totalParts = count;
            nextPart = index + 1;
            body.append(frame, end + 2, frame.length());
            if (index == count) {
                emit.accept(body.toString());
                body.setLength(0);
                nextPart = 0;
                totalParts = 0;
                while (!pending.isEmpty()) emit.accept(pending.removeFirst());
            }
        }

        synchronized void finishIncomplete(Consumer<String> emit) {
            if (nextPart != 0) {
                emit.accept("[Lịch sử chưa nhận đủ; kết nối đã kết thúc.]");
                body.setLength(0);
                nextPart = 0;
                totalParts = 0;
            }
            while (!pending.isEmpty()) emit.accept(pending.removeFirst());
        }
    }
}
