package studyfix;

import java.io.*;
import java.nio.charset.StandardCharsets;

// Dùng chung cho client và server để không viết lặp hàm gửi/nhận.
public final class Protocol {
    public static final int MAX_BYTES = 65536;
    private Protocol() { }

    /** Chia dữ liệu đúng ranh giới ký tự Unicode và chừa chỗ cho tiêu đề của từng phần. */
    public static java.util.List<String> historyFrames(String message) {
        if (message.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES)
            return java.util.List.of(message);
        java.util.List<String> parts = new java.util.ArrayList<>();
        int start = 0, bytes = 0;
        for (int i = 0; i < message.length();) {
            int cp = message.codePointAt(i);
            int size = cp <= 0x7f ? 1 : cp <= 0x7ff ? 2 : cp <= 0xffff ? 3 : 4;
            if (bytes + size > MAX_BYTES - 128) {
                parts.add(message.substring(start, i)); start = i; bytes = 0;
            }
            bytes += size; i += Character.charCount(cp);
        }
        parts.add(message.substring(start));
        int count = parts.size();
        for (int i = 0; i < count; i++)
            parts.set(i, "[HISTORY_PART " + (i+1) + "/" + count + "]\n" + parts.get(i));
        return java.util.List.copyOf(parts);
    }

    public static String receive(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_BYTES) {
            throw new IOException("Độ dài thông điệp không hợp lệ");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void send(DataOutputStream out, String message) throws IOException {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) {
            throw new IOException("Thông điệp vượt giới hạn 64 KiB");
        }
        // Khóa cả frame: các luồng không được ghi xen header và nội dung.
        synchronized (out) {
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        }
    }
}
