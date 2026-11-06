package vn.studyfix.client.screen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client endpoint for the planned screen-share relay protocol v1. Server extension is currently required. */
public final class ScreenShareClient implements AutoCloseable {
    public enum Mode { PUBLISH, VIEW }
    public interface Listener { default void onImage(BufferedImage image) { } default void onStatus(String status) { } default void onClosed(Throwable reason) { } }
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Mode mode;
    private final Listener listener;
    private final ScreenCaptureService capture = new ScreenCaptureService(1280, 0.62f);
    private final ScheduledExecutorService frames = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "studyfix-screen-frame-sender"); t.setDaemon(true); return t; });
    private final AtomicBoolean closed = new AtomicBoolean();

    private ScreenShareClient(String host, int port, String ticket, Mode mode, Listener listener) throws IOException {
        this.mode = mode; this.listener = listener == null ? new Listener() { } : listener;
        socket = new Socket(); socket.connect(new InetSocketAddress(host, port), 5000); socket.setTcpNoDelay(true); socket.setKeepAlive(true);
        in = new DataInputStream(socket.getInputStream()); out = new DataOutputStream(socket.getOutputStream());
        ScreenShareWire.write(out, ScreenShareWire.Type.HELLO, ScreenShareWire.text(ticket));
        Thread reader = new Thread(this::readLoop, "studyfix-screen-reader"); reader.setDaemon(true); reader.start();
        if (mode == Mode.PUBLISH) frames.scheduleWithFixedDelay(this::sendCapture, 0, 400, TimeUnit.MILLISECONDS);
    }
    public static ScreenShareClient connect(String host, int port, String ticket, Mode mode, Listener listener) throws IOException {
        if (ticket == null || ticket.isBlank()) throw new IllegalArgumentException("Ticket chia sẻ màn hình rỗng.");
        return new ScreenShareClient(host, port, ticket, mode, listener);
    }
    private void sendCapture() {
        if (closed.get()) return;
        try { byte[] jpeg = capture.captureJpeg(); ScreenShareWire.write(out, ScreenShareWire.Type.FRAME, jpeg); }
        catch (Exception e) { listener.onStatus("Không chụp/gửi được ảnh: " + e.getMessage()); }
    }
    private void readLoop() {
        try {
            while (!closed.get()) {
                ScreenShareWire.Packet packet = ScreenShareWire.read(in);
                switch (packet.type()) {
                    case FRAME -> {
                        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(packet.payload()));
                        if (image != null) listener.onImage(image);
                    }
                    case STATUS -> listener.onStatus(ScreenShareWire.text(packet.payload()));
                    case ERROR -> { listener.onStatus("Lỗi server màn hình: " + ScreenShareWire.text(packet.payload())); closeInternal(new IOException("Relay từ chối kết nối")); return; }
                    case STOP -> { listener.onStatus("Luồng chia sẻ màn hình đã dừng."); closeInternal(null); return; }
                    case HELLO -> listener.onStatus("Server gửi packet HELLO không mong đợi.");
                }
            }
        } catch (IOException e) { if (!closed.get()) closeInternal(e); }
    }
    private void closeInternal(Throwable cause) {
        if (!closed.compareAndSet(false, true)) return;
        frames.shutdownNow(); try { socket.close(); } catch (IOException ignored) { }
        if (cause != null) listener.onClosed(cause); else listener.onClosed(null);
    }
    @Override public void close() {
        if (closed.get()) return;
        try { ScreenShareWire.write(out, ScreenShareWire.Type.STOP, new byte[0]); } catch (IOException ignored) { }
        closeInternal(null);
    }
    public Mode mode() { return mode; }
    public boolean isClosed() { return closed.get(); }
}
