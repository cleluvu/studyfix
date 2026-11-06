package vn.studyfix.client.net;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reassembles the server's [HISTORY_PART i/n] frames into one logical message. */
public final class HistoryFrameAssembler {
    private static final Pattern HEADER = Pattern.compile("^\\[HISTORY_PART (\\d+)/(\\d+)]\\n([\\s\\S]*)$");
    private int expected = 0;
    private int next = 1;
    private final Map<Integer, String> parts = new HashMap<>();

    public synchronized Optional<String> accept(String frame) {
        Matcher m = HEADER.matcher(frame);
        if (!m.matches()) {
            reset();
            return Optional.of(frame);
        }
        int index = Integer.parseInt(m.group(1));
        int count = Integer.parseInt(m.group(2));
        if (count < 1 || index < 1 || index > count) { reset(); return Optional.empty(); }
        if (index == 1) { reset(); expected = count; }
        if (expected != count || index != next) { reset(); return Optional.empty(); }
        parts.put(index, m.group(3)); next++;
        if (next <= expected) return Optional.empty();
        StringBuilder result = new StringBuilder();
        for (int i = 1; i <= expected; i++) result.append(parts.getOrDefault(i, ""));
        String assembled = result.toString(); reset(); return Optional.of(assembled);
    }
    private void reset() { expected = 0; next = 1; parts.clear(); }
}
