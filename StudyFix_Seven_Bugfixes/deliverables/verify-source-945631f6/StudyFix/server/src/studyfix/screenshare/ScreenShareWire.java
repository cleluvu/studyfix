package studyfix.screenshare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Giao thức nhị phân cho cổng TCP chia sẻ màn hình riêng, tách khỏi giao thức điều khiển dạng văn bản UTF-8. */
public final class ScreenShareWire {
    public static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    public enum Type {
        HELLO(1), FRAME(2), STOP(3), ERROR(4), STATUS(5);
        private final int id;
        Type(int id) { this.id = id; }
        static Type from(int id) throws IOException {
            for (Type type : values()) if (type.id == id) return type;
            throw new IOException("Unknown screen packet type=" + id);
        }
    }
    public record Packet(Type type, byte[] payload) { }
    private ScreenShareWire() { }
    public static Packet read(DataInputStream in) throws IOException {
        final int length;
        try { length = in.readInt(); } catch (EOFException e) { throw e; }
        if (length < 1 || length > MAX_PACKET_BYTES) throw new IOException("Screen packet length out of range: " + length);
        byte[] body = new byte[length]; in.readFully(body);
        return new Packet(Type.from(body[0] & 0xff), Arrays.copyOfRange(body, 1, body.length));
    }
    public static void write(DataOutputStream out, Type type, byte[] payload) throws IOException {
        byte[] data = payload == null ? new byte[0] : payload;
        long length = 1L + data.length;
        if (length > MAX_PACKET_BYTES) throw new IOException("Screen packet too large");
        synchronized (out) { out.writeInt((int) length); out.writeByte(type.id); out.write(data); out.flush(); }
    }
    public static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    public static String fromUtf8(byte[] value) { return new String(value, StandardCharsets.UTF_8); }
}
