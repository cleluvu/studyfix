package studyfix.person3;

import studyfix.person3.Models.TestCase;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compiles and runs student {@code Main.java} against stdin/stdout test cases.
 * Runs off the ClientHandler reader thread; never holds Room locks.
 */
public final class GradingWorker implements AutoCloseable {
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "studyfix-grader");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean closed = new AtomicBoolean();

    public record GradeResult(int score, int maxScore, String details) { }

    public void gradeAsync(long submissionId, String code, int maxScore, List<TestCase> tests,
                    GradeCallback callback) {
        if (closed.get()) {
            callback.completed(submissionId, new GradeResult(0, maxScore, "Worker chấm đã dừng."));
            return;
        }
        pool.execute(() -> {
            try {
                callback.completed(submissionId, grade(code, maxScore, tests));
            } catch (Exception e) {
                callback.completed(submissionId,
                        new GradeResult(0, maxScore, "Lỗi worker: " + e.getMessage()));
            }
        });
    }

    @FunctionalInterface
    public interface GradeCallback {
        void completed(long submissionId, GradeResult result);
    }

    public GradeResult grade(String code, int maxScore, List<TestCase> tests) {
        if (tests == null || tests.isEmpty()) {
            return new GradeResult(0, maxScore,
                    "Không có test case — chờ giáo viên chấm tay (điểm tạm 0). Dùng /grade set.");
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("studyfix-grade-");
            Path source = dir.resolve("Main.java");
            Files.writeString(source, code, StandardCharsets.UTF_8);

            String javac = findTool("javac");
            String java = findTool("java");
            ProcessCompile compile = run(List.of(javac, "-encoding", "UTF-8", source.getFileName().toString()),
                    dir, "", 20_000);
            if (compile.exitCode != 0) {
                return new GradeResult(0, maxScore,
                        "Biên dịch thất bại:\n" + trim(compile.stderr.isBlank() ? compile.stdout : compile.stderr, 1500));
            }

            int passed = 0;
            StringBuilder log = new StringBuilder();
            for (int i = 0; i < tests.size(); i++) {
                TestCase tc = tests.get(i);
                ProcessCompile run = run(List.of(java, "-Dfile.encoding=UTF-8", "Main"),
                        dir, tc.stdin() == null ? "" : tc.stdin(), 8_000);
                String actual = normalize(run.stdout);
                String expected = normalize(tc.expectedStdout());
                boolean ok = run.exitCode == 0 && actual.equals(expected);
                if (ok) {
                    passed++;
                    log.append("Test ").append(i + 1).append(": PASS\n");
                } else {
                    log.append("Test ").append(i + 1).append(": FAIL");
                    if (run.exitCode != 0) {
                        log.append(" (exit=").append(run.exitCode).append(")");
                        if (!run.stderr.isBlank()) log.append("\n  stderr: ").append(trim(run.stderr, 400));
                    }
                    log.append("\n  expected: ").append(preview(expected));
                    log.append("\n  actual:   ").append(preview(actual)).append('\n');
                }
            }
            int score = (int) Math.round((passed * (double) maxScore) / tests.size());
            log.append("Kết quả: ").append(passed).append('/').append(tests.size())
                    .append(" → ").append(score).append('/').append(maxScore);
            return new GradeResult(score, maxScore, log.toString());
        } catch (Exception e) {
            return new GradeResult(0, maxScore, "Không chấm được: " + e.getMessage());
        } finally {
            if (dir != null) deleteTree(dir);
        }
    }

    private record ProcessCompile(int exitCode, String stdout, String stderr) { }

    private static ProcessCompile run(List<String> command, Path dir, String stdin, long timeoutMs)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        try (OutputStream out = p.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
                if (!stdin.endsWith("\n")) out.write('\n');
            }
        }
        boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            p.waitFor(2, TimeUnit.SECONDS);
            return new ProcessCompile(-1, "", "TIMEOUT sau " + timeoutMs + "ms");
        }
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessCompile(p.exitValue(), stdout, stderr);
    }

    private static String findTool(String name) {
        String home = System.getProperty("java.home");
        if (home != null) {
            Path bin = Path.of(home, "bin", name + (isWindows() ? ".exe" : ""));
            if (Files.isExecutable(bin)) return bin.toString();
            // jre-only home: try sibling jdk bin
            Path alt = Path.of(home).getParent() == null ? null
                    : Path.of(home).resolveSibling("bin").resolve(name + (isWindows() ? ".exe" : ""));
            if (alt != null && Files.isExecutable(alt)) return alt.toString();
        }
        return name;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        String n = s.strip();
        return n.length() <= max ? n : n.substring(0, max) + "…";
    }

    private static String preview(String s) {
        String one = s.replace('\n', '⏎');
        return one.length() <= 120 ? one : one.substring(0, 120) + "…";
    }

    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        pool.shutdownNow();
    }
}
