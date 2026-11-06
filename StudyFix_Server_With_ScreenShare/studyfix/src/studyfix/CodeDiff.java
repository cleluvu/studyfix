package studyfix;

import java.util.*;

/** Exact line endings; bounded LCS/Myers after stripping common prefix and suffix. */
public final class CodeDiff {
    public static final String INITIAL = "Code khởi tạo (bản 0).";
    public static final String MISSING = "Không đủ dữ liệu đối chiếu: thiếu phiên bản trước.";
    private static final int MAX_CELLS = 1_000_000;
    private CodeDiff() { }

    /** Render the stored, exact diff without changing its persistence format. */
    public static String display(String changes) {
        if (!changes.startsWith("Số dòng - theo bản trước, + theo bản sau;")) {
            return changes;
        }
        List<ChangedLine> removed = new ArrayList<>(), added = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        java.util.regex.Pattern rowPattern = java.util.regex.Pattern.compile(
                "^([+-]) \\[([0-9]+)\\] (.*) ⟦(LF|CRLF|CR|không xuống dòng)⟧$");
        for (String row : changes.split("\n", -1)) {
            if (row.startsWith("@@") || row.startsWith("Vùng thay đổi (")) {
                appendChanges(result, removed, added);
                if (row.startsWith("Vùng thay đổi (")) {
                    result.append("Vùng thay đổi lớn; hiển thị toàn bộ vùng để không bỏ sót.\n\n");
                }
                continue;
            }
            java.util.regex.Matcher match = rowPattern.matcher(row);
            if (match.matches()) {
                ChangedLine line = new ChangedLine(Integer.parseInt(match.group(2)),
                        match.group(3), match.group(4));
                (match.group(1).equals("-") ? removed : added).add(line);
            } else if (!row.isEmpty() && !row.startsWith("Số dòng - theo bản trước,")) {
                // Keep an unknown stored format intact rather than hide its contents.
                return changes;
            }
        }
        appendChanges(result, removed, added);
        return result.length() == 0 ? "Không có thay đổi nội dung" : result.toString().stripTrailing();
    }

    private record ChangedLine(int number, String text, String ending) { }

    private static void appendChanges(StringBuilder out, List<ChangedLine> removed,
                                      List<ChangedLine> added) {
        // A contiguous replacement block has no semantic mapping between its lines.
        // Only label an individual replacement when exactly one line changes on each side.
        if (removed.size() == 1 && added.size() == 1) {
            ChangedLine before = removed.get(0), after = added.get(0);
            if (before.text().equals(after.text())) {
                out.append("Dòng ").append(before.number()).append(" (bản trước) → ")
                        .append(after.number()).append(" (bản sau): chỉ thay đổi xuống dòng.\n\n");
            } else {
                out.append("Dòng ").append(before.number());
                if (before.number() != after.number()) {
                    out.append(" (bản trước) → dòng ").append(after.number()).append(" (bản sau)");
                }
                out.append(":\n  Trước: ").append(before.text())
                        .append("\n  Sau:   ").append(after.text()).append("\n\n");
            }
        } else {
            if (!removed.isEmpty()) {
                out.append(added.isEmpty() ? "Dòng bị xóa (bản trước):\n" : "Trước khi sửa:\n");
                for (ChangedLine line : removed) out.append("  Dòng ").append(line.number())
                        .append(": ").append(line.text()).append('\n');
            }
            if (!added.isEmpty()) {
                out.append(removed.isEmpty() ? "Dòng được thêm (bản sau):\n" : "Sau khi sửa:\n");
                for (ChangedLine line : added) out.append("  Dòng ").append(line.number())
                        .append(": ").append(line.text()).append('\n');
            }
            if (!removed.isEmpty() || !added.isEmpty()) out.append('\n');
        }
        removed.clear(); added.clear();
    }

    static List<String> lines(String code) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < code.length() && code.charAt(i + 1) == '\n') i++;
                result.add(code.substring(start, i + 1)); start = i + 1;
            }
        }
        if (start < code.length()) result.add(code.substring(start));
        return result;
    }
    private static String visible(String line) {
        String ending = "không xuống dòng";
        int end = line.length();
        if (line.endsWith("\r\n")) { ending = "CRLF"; end -= 2; }
        else if (line.endsWith("\n")) { ending = "LF"; end--; }
        else if (line.endsWith("\r")) { ending = "CR"; end--; }
        return line.substring(0, end) + " ⟦" + ending + "⟧";
    }
    private static void row(StringBuilder out, char sign, int line, String text) {
        out.append(sign).append(" [").append(line).append("] ").append(visible(text)).append('\n');
    }
    /** Myers for large, sparsely edited regions. Null means the fixed budget was exhausted. */
    private static List<Character> sparse(List<String> a, List<String> b, int start, int n, int m) {
        int max = n + m, offset = max + 1, width = 2 * max + 3;
        int[] v = new int[width];
        List<int[]> trace = new ArrayList<>();
        int work = 0;
        for (int d=0; d<=max; d++) {
            // At most 4 million trace ints (~16 MB), independent of line counts/product.
            if ((long)(trace.size()+1)*width > 4_000_000) return null;
            for (int k=-d; k<=d; k+=2) {
                if (++work > 2_000_000) return null;
                int x = k == -d || (k != d && v[offset+k-1] < v[offset+k+1])
                        ? v[offset+k+1] : v[offset+k-1]+1;
                int y = x-k;
                while (x<n && y<m && a.get(start+x).equals(b.get(start+y))) {
                    if (++work > 2_000_000) return null;
                    x++; y++;
                }
                v[offset+k]=x;
                if (x>=n && y>=m) {
                    List<Character> ops = new ArrayList<>();
                    for (int depth=d; depth>0; depth--) {
                        int[] previous = trace.get(depth-1);
                        int diagonal=x-y;
                        int previousK = diagonal == -depth || (diagonal != depth
                                && previous[offset+diagonal-1] < previous[offset+diagonal+1])
                                ? diagonal+1 : diagonal-1;
                        int px=previous[offset+previousK], py=px-previousK;
                        while (x>px && y>py) { ops.add(' '); x--; y--; }
                        if (x==px) { ops.add('+'); y--; } else { ops.add('-'); x--; }
                    }
                    while (x>0 && y>0) { ops.add(' '); x--; y--; }
                    Collections.reverse(ops); return ops;
                }
            }
            trace.add(v.clone());
        }
        return null;
    }
    public static String between(String before, String after) {
        if (before.equals(after)) return "Không có thay đổi nội dung";
        List<String> a = lines(before), b = lines(after);
        int start = 0, ae = a.size(), be = b.size();
        while (start < ae && start < be && a.get(start).equals(b.get(start))) start++;
        while (ae > start && be > start && a.get(ae-1).equals(b.get(be-1))) { ae--; be--; }
        int n = ae-start, m = be-start;
        StringBuilder out = new StringBuilder("Số dòng - theo bản trước, + theo bản sau; ký hiệu cuối dòng trong ⟦ ⟧.\n");
        if ((long)(n+1)*(m+1) > MAX_CELLS) {
            List<Character> ops = sparse(a, b, start, n, m);
            if (ops != null) {
                int i=start, j=start; boolean hunk=false;
                for (char op : ops) {
                    if (op==' ') { i++; j++; hunk=false; continue; }
                    if (!hunk) { out.append("@@ dòng cũ ").append(i+1).append(", dòng mới ").append(j+1).append(" @@\n"); hunk=true; }
                    if (op=='-') { row(out, '-', i+1, a.get(i)); i++; }
                    else { row(out, '+', j+1, b.get(j)); j++; }
                }
                return out.toString();
            }
            out.append("Vùng thay đổi (vượt ngân sách LCS/Myers; hiển thị toàn vùng giữa tiền tố/hậu tố chung):\n");
            for (int i=start; i<ae; i++) row(out, '-', i+1, a.get(i));
            for (int j=start; j<be; j++) row(out, '+', j+1, b.get(j));
            return out.toString();
        }
        int[][] lcs = new int[n+1][m+1];
        for (int i=n-1; i>=0; i--) for (int j=m-1; j>=0; j--)
            lcs[i][j] = a.get(start+i).equals(b.get(start+j)) ? 1+lcs[i+1][j+1]
                    : Math.max(lcs[i+1][j], lcs[i][j+1]);
        int i=0, j=0; boolean hunk=false;
        while (i<n || j<m) {
            if (i<n && j<m && a.get(start+i).equals(b.get(start+j))) { i++; j++; hunk=false; }
            else {
                if (!hunk) { out.append("@@ dòng cũ ").append(start+i+1).append(", dòng mới ").append(start+j+1).append(" @@\n"); hunk=true; }
                if (i<n && (j==m || lcs[i+1][j]>=lcs[i][j+1])) { row(out, '-', start+i+1, a.get(start+i)); i++; }
                else { row(out, '+', start+j+1, b.get(start+j)); j++; }
            }
        }
        return out.toString();
    }
}
