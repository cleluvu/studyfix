package studyfix;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Một bản nháp dùng số dòng của bản code gốc lúc bắt đầu lượt sửa.
 * Room giữ khóa khi xử lý bản nháp; client không được trực tiếp thay đổi đối tượng này. */
public final class EditSession {
    private enum Kind { REPLACE, DELETE, KEEP }
    private record Change(Kind kind, String text) { }
    private final List<String> original;
    private final TreeMap<Integer, Change> selected = new TreeMap<>();
    private final boolean range;
    private final int first, last;
    private Change rangeChange;
    private final long version;

    public EditSession(String code, long version, String selection) {
        this.version = version;
        original = List.copyOf(CodeDiff.lines(code));
        range = selection.contains("-");
        try {
            if (range) {
                if (!selection.matches("[0-9]+-[0-9]+")) throw new IllegalArgumentException();
                String[] values = selection.split("-");
                first = Integer.parseInt(values[0]); last = Integer.parseInt(values[1]);
                checkLine(first); checkLine(last);
                if (first > last) throw new IllegalArgumentException();
            } else {
                if (!selection.matches("[0-9]+(,[0-9]+)*")) throw new IllegalArgumentException();
                for (String value : selection.split(",")) {
                    int line = Integer.parseInt(value); checkLine(line);
                    if (selected.containsKey(line)) throw new IllegalArgumentException();
                    selected.put(line, null);
                }
                first = selected.firstKey(); last = selected.lastKey();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Chọn dòng hợp lệ: 2,5,8 hoặc đoạn 4-7; không trùng, không vượt số dòng.");
        }
    }
    public long version() { return version; }
    public boolean isRange() { return range; }
    private void checkLine(int n) {
        if (n < 1 || n > original.size()) throw new IllegalArgumentException();
    }
    private static String endingOf(String s) {
        return s.endsWith("\r\n") ? "\r\n" : s.endsWith("\n") ? "\n" : s.endsWith("\r") ? "\r" : "";
    }
    private static String content(String s) { return s.substring(0, s.length() - endingOf(s).length()); }
    private static void limit(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > 32000)
            throw new IllegalArgumentException("Bản nháp vượt 32000 byte UTF-8.");
    }
    public void setLine(int line, String text) { changeLine(line, Kind.REPLACE, text); }
    public void deleteLine(int line) { changeLine(line, Kind.DELETE, ""); }
    public void keepLine(int line) { changeLine(line, Kind.KEEP, ""); }
    private void changeLine(int line, Kind kind, String text) {
        if (range || !selected.containsKey(line)) throw new IllegalArgumentException("Dòng không nằm trong lựa chọn từng dòng.");
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Sửa từng dòng không nhận ký tự xuống dòng; dùng chế độ đoạn.");
        limit(text);
        Change old = selected.put(line, new Change(kind, text));
        try { limit(build()); } catch (IllegalArgumentException e) { selected.put(line, old); throw e; }
    }
    public void setRange(String text) { changeRange(new Change(Kind.REPLACE, text)); }
    public void deleteRange() { changeRange(new Change(Kind.DELETE, "")); }
    public void keepRange() { changeRange(new Change(Kind.KEEP, "")); }
    private void changeRange(Change change) {
        if (!range) throw new IllegalArgumentException("Chưa chọn chế độ đoạn liên tiếp.");
        limit(change.text());
        Change old = rangeChange; rangeChange = change;
        try { limit(build()); } catch (IllegalArgumentException e) { rangeChange = old; throw e; }
    }
    public boolean complete() {
        return range ? rangeChange != null : selected.values().stream().allMatch(Objects::nonNull);
    }
    private String replacement(Change change, String oldEnding) {
        if (change.kind() == Kind.DELETE) return "";
        // Giữ ký tự ngăn cách trước dòng gốc tiếp theo để không nối nhầm hai câu lệnh.
        String text = change.text();
        return !oldEnding.isEmpty() && endingOf(text).isEmpty() ? text + oldEnding : text;
    }
    public String build() {
        StringBuilder out = new StringBuilder();
        for (int n = 1; n <= original.size(); n++) {
            if (range && n == first && rangeChange != null && rangeChange.kind() != Kind.KEEP) {
                out.append(replacement(rangeChange, endingOf(original.get(last - 1))));
                n = last; continue;
            }
            Change change = range ? null : selected.get(n);
            if (change == null || change.kind() == Kind.KEEP) out.append(original.get(n - 1));
            else if (change.kind() == Kind.REPLACE) out.append(change.text()).append(endingOf(original.get(n - 1)));
        }
        return out.toString();
    }
    public String status() {
        StringBuilder out = new StringBuilder("[Bản nháp] version gốc=" + version + "; chưa lưu code chung.\n");
        if (range) {
            out.append("Đoạn ").append(first).append('-').append(last).append(": ")
                    .append(rangeChange == null ? "Chưa xử lý" : rangeChange.kind() == Kind.KEEP ? "Giữ nguyên" : "Đã xử lý").append('\n');
            out.append(String.join("", original.subList(first - 1, last)));
        } else {
            selected.forEach((line, change) -> out.append("Dòng ").append(line).append(": ")
                    .append(change == null ? "Chưa xử lý" : change.kind() == Kind.KEEP ? "Giữ nguyên" : change.kind() == Kind.DELETE ? "Đã chọn xóa" : "Đã sửa")
                    .append(" | Gốc: ").append(content(original.get(line - 1))).append('\n'));
            Integer next = selected.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).findFirst().orElse(null);
            out.append(next == null ? "Đã xử lý đủ. Dùng /finish token để lưu cả lượt." : "Dòng tiếp theo: " + next);
        }
        return out.toString();
    }
    public String changes() {
        if (!complete()) throw new IllegalArgumentException("Chưa xử lý đủ các dòng đã chọn.");
        StringBuilder out = new StringBuilder("Vị trí dưới đây theo phiên bản trước lượt sửa.\n");
        if (range) {
            out.append("Đoạn dòng ").append(first).append('-').append(last).append(':').append('\n');
            append(out, rangeChange, String.join("", original.subList(first - 1, last)),
                    rangeChange.kind() == Kind.REPLACE ? replacement(rangeChange, endingOf(original.get(last - 1))) : "");
        } else {
            selected.forEach((line, change) -> {
                out.append("Dòng ").append(line).append(':').append('\n');
                append(out, change, content(original.get(line - 1)), change.text());
            });
        }
        return out.toString().stripTrailing();
    }
    private static void append(StringBuilder out, Change change, String before, String after) {
        if (change.kind() == Kind.KEEP || (change.kind() == Kind.REPLACE && before.equals(after))) {
            out.append("  Giữ nguyên\n\n"); return;
        }
        out.append("  Trước:\n").append(before).append("\n  Sau:\n")
                .append(change.kind() == Kind.DELETE ? "[Đã xóa]" : after.isEmpty() ? "[Dòng trống]" : after).append("\n\n");
    }
}
