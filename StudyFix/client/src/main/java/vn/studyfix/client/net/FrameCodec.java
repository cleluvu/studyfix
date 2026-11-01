package vn.studyfix.client.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Exactly matches studyfix.Protocol: signed big-endian int length then UTF-8 bytes. */
public final class FrameCodec {
    public static final int MAX_FRAME_BYTES = 65_536;
    private FrameCodec() { }
    public static String read(DataInputStream in) throws IOException {
        int length;
        try { length = in.readInt(); }
        catch (EOFException e) { throw e; }
        if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("Frame length ngoài giới hạn: " + length);
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }
    public static void write(DataOutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_FRAME_BYTES) throw new IOException("Payload UTF-8 vượt 65536 byte");
        synchronized (out) {
            out.writeInt(payload.length); out.write(payload); out.flush();
        }
    }
}
