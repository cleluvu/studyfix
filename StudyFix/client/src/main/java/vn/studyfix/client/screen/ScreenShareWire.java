package vn.studyfix.client.screen;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Draft v1 binary framing for the dedicated screen-share TCP port; see docs/SERVER_GAPS_AND_SCREEN_SHARE.md. */
public final class ScreenShareWire {
    public static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    public enum Type { HELLO(1), FRAME(2), STOP(3), ERROR(4), STATUS(5); final int id; Type(int id) { this.id = id; }
        static Type from(int id) throws IOException { for (Type t : values()) if (t.id == id) return t; throw new IOException("Loại packet màn hình không hợp lệ: " + id); } }
    public record Packet(Type type, byte[] payload) { }
    private ScreenShareWire() { }
    public static Packet read(DataInputStream in) throws IOException {
        final int length;
        try { length = in.readInt(); } catch (EOFException e) { throw e; }
        if (length < 1 || length > MAX_PACKET_BYTES) throw new IOException("Packet màn hình ngoài giới hạn: " + length);
        byte[] body = new byte[length]; in.readFully(body);
        return new Packet(Type.from(body[0] & 0xff), java.util.Arrays.copyOfRange(body, 1, body.length));
    }
    public static void write(DataOutputStream out, Type type, byte[] payload) throws IOException {
        byte[] bytes = payload == null ? new byte[0] : payload;
        long length = 1L + bytes.length;
        if (length > MAX_PACKET_BYTES) throw new IOException("Packet màn hình vượt giới hạn");
        synchronized (out) { out.writeInt((int) length); out.writeByte(type.id); out.write(bytes); out.flush(); }
    }
    public static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    public static String text(byte[] value) { return new String(value, StandardCharsets.UTF_8); }
}
