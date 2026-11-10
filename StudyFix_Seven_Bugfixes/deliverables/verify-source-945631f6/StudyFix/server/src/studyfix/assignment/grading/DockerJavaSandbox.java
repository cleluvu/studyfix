package studyfix.assignment.grading;

import studyfix.assignment.model.Exercise;
import studyfix.assignment.model.GradingResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chấm bài trong môi trường Docker riêng. Không ghi đầu vào hay đáp án của test ẩn
 * vào thư mục mà bài nộp truy cập được. Đầu vào từng test được gửi qua stdin
 * cho container mới không có mạng; đáp án chuẩn chỉ nằm ở server.
 */
public final class DockerJavaSandbox implements GradingSandbox {
    private static final int MAX_SOURCE_BYTES = 32 * 1024;
    private static final int MAX_STDOUT_BYTES = 512 * 1024;
    private static final int MAX_STDERR_BYTES = 16 * 1024;
    private static final int MAX_DIAGNOSTIC_BYTES = 12 * 1024;

    private final String docker;
    private final String image;
    private final int perTestSeconds;
    private final long totalTimeoutSeconds;

    public DockerJavaSandbox(String image, int perTestSeconds, long totalTimeoutSeconds) {
        this.docker = System.getProperty("studyfix.assignment.docker", "docker");
        this.image = Objects.requireNonNull(image);
        this.perTestSeconds = Math.max(1, perTestSeconds);
        this.totalTimeoutSeconds = Math.max(1, totalTimeoutSeconds);
    }

    @Override
    public GradingResult grade(String submissionId, String source,
                               List<Exercise.TestCase> tests, double maxScore) throws Exception {
        long started = System.nanoTime();
        if (tests == null || tests.isEmpty()) {
            return result("INTERNAL_ERROR", 0, maxScore, 0, 0,
                    "Bài chưa có test case.", started, List.of());
        }
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            return result("INTERNAL_ERROR", 0, maxScore, 0, tests.size(),
                    "Source code vượt giới hạn cho phép.", started, List.of());
        }

        Path work = Files.createTempDirectory("studyfix-grade-");
        try {
            // Chỉ đưa các dữ liệu này vào container biên dịch và chạy bài nộp.
            Files.writeString(work.resolve("Main.java"), source, StandardCharsets.UTF_8);
            long deadlineNanos = started + TimeUnit.SECONDS.toNanos(totalTimeoutSeconds);
            String compileName = containerName("compile");
            List<String> compileCmd = dockerBase(compileName, work, false);
            compileCmd.addAll(List.of("sh", "-lc",
                    "javac -encoding UTF-8 Main.java >compile.out 2>compile.err; rc=$?; echo $rc >compile.rc; exit 0"));

            ProcessResult compile = runProcess(compileCmd, null, remainingMillis(deadlineNanos),
                    MAX_DIAGNOSTIC_BYTES, MAX_DIAGNOSTIC_BYTES, null);
            if (compile.timedOut()) {
                killContainer(compileName);
                return result("TIME_LIMIT_EXCEEDED", 0, maxScore, 0, tests.size(),
                        "Biên dịch vượt quá thời gian giới hạn.", started, List.of());
            }

            Path compileRc = work.resolve("compile.rc");
            if (!Files.exists(compileRc)) {
                String diagnostic = safe(compile.stderrText() + "\n" + compile.stdoutText());
                return unavailable(started, "Docker sandbox chưa sẵn sàng hoặc không thể chạy container. " + diagnostic,
                        maxScore, tests.size());
            }
            int compileExit = parseInt(Files.readString(compileRc, StandardCharsets.UTF_8), 1);
            if (compileExit != 0) {
                String diagnostic = Files.exists(work.resolve("compile.err"))
                        ? safe(Files.readString(work.resolve("compile.err"), StandardCharsets.UTF_8))
                        : "Lỗi biên dịch Java.";
                return result("COMPILE_ERROR", 0, maxScore, 0, tests.size(), diagnostic, started, List.of());
            }

            int totalWeight = tests.stream().mapToInt(Exercise.TestCase::weight).sum();
            int passed = 0;
            int earned = 0;
            boolean timeout = false, runtimeError = false, outputLimit = false, memoryLimit = false;
            List<Map<String, Object>> testResults = new ArrayList<>();

            for (int i = 0; i < tests.size(); i++) {
                Exercise.TestCase test = tests.get(i);
                long remaining = remainingMillis(deadlineNanos);
                if (remaining <= 0) {
                    timeout = true;
                    testResults.add(testRow(test, false, "Chạm giới hạn thời gian tổng.", 0));
                    continue;
                }

                String runName = containerName("test");
                List<String> command = dockerBase(runName, work, true);
                command.addAll(List.of("java", "-Xmx64m", "-XX:ActiveProcessorCount=1", "-cp", "/code", "Main"));
                ProcessResult execution = runProcess(command,
                        test.input().getBytes(StandardCharsets.UTF_8),
                        Math.min(remaining, TimeUnit.SECONDS.toMillis(perTestSeconds)),
                        MAX_STDOUT_BYTES, MAX_STDERR_BYTES, runName);

                if (execution.outputExceeded()) {
                    outputLimit = true;
                    testResults.add(testRow(test, false, "Vượt giới hạn đầu ra.", execution.elapsedMillis()));
                    continue;
                }
                if (execution.timedOut()) {
                    timeout = true;
                    killContainer(runName);
                    testResults.add(testRow(test, false, "Vượt thời gian chạy.", execution.elapsedMillis()));
                    continue;
                }
                if (execution.startFailure() != null) {
                    return unavailable(started, "Không thể khởi chạy Docker: " + safe(execution.startFailure()),
                            maxScore, tests.size());
                }
                if (execution.exitCode() == 125) {
                    return unavailable(started,
                            "Docker không thể khởi chạy container cho lượt test: " + safe(execution.stderrText()),
                            maxScore, tests.size());
                }
                if (execution.exitCode() != 0) {
                    if (execution.exitCode() == 137) memoryLimit = true;
                    else runtimeError = true;
                    String diagnostic = execution.exitCode() == 137
                            ? "Container bị kết thúc do có thể vượt giới hạn bộ nhớ."
                            : "Chương trình lỗi khi chạy: " + safe(execution.stderrText());
                    testResults.add(testRow(test, false, diagnostic, execution.elapsedMillis()));
                    continue;
                }

                String actual = execution.stdoutText();
                boolean ok = normalize(actual).equals(normalize(test.expected()));
                if (ok) {
                    passed++;
                    earned += test.weight();
                }
                String message = ok ? "Đạt" : (test.hidden()
                        ? "Hidden test không đạt."
                        : "Sai kết quả. Output thực tế: " + safe(actual));
                testResults.add(testRow(test, ok, message, execution.elapsedMillis()));
            }

            String verdict;
            if (passed == tests.size()) verdict = "ACCEPTED";
            else if (outputLimit) verdict = "OUTPUT_LIMIT_EXCEEDED";
            else if (memoryLimit) verdict = "MEMORY_LIMIT_EXCEEDED";
            else if (timeout) verdict = "TIME_LIMIT_EXCEEDED";
            else if (runtimeError) verdict = "RUNTIME_ERROR";
            else verdict = "WRONG_ANSWER";

            double score = totalWeight <= 0 ? 0 : Math.floor(maxScore * earned / totalWeight);
            String feedback = passed == tests.size()
                    ? "Tất cả test đã thiết kế đều đạt."
                    : "Đạt " + passed + "/" + tests.size()
                        + " test. Input/đáp án của hidden test không được tiết lộ.";
            return result(verdict, score, maxScore, passed, tests.size(), feedback, started, testResults);
        } finally {
            try (var paths = Files.walk(work)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
            }
        }
    }

    private List<String> dockerBase(String name, Path work, boolean readOnlyCode) {
        List<String> command = new ArrayList<>(List.of(
                docker, "run", "--pull=never", "--rm", "--name", name,
                "--network", "none", "--memory=128m", "--memory-swap=128m", "--cpus=0.5",
                "--pids-limit=32", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "--tmpfs", "/tmp:rw,exec,nosuid,size=16m",
                "--mount", "type=bind,source=" + work.toAbsolutePath() + ",target=" + (readOnlyCode ? "/code,readonly" : "/work")));
        if (readOnlyCode) command.addAll(List.of("-i", "-w", "/tmp", image));
        else command.addAll(List.of("-w", "/work", image));
        return command;
    }

    private ProcessResult runProcess(List<String> command, byte[] stdin, long timeoutMillis,
                                     int stdoutLimit, int stderrLimit, String containerName) {
        long started = System.nanoTime();
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            return ProcessResult.failed(e.getMessage(), elapsed(started));
        }
        BoundedCapture stdout = new BoundedCapture(stdoutLimit);
        BoundedCapture stderr = new BoundedCapture(stderrLimit);
        Thread stdoutReader = drain(process.getInputStream(), stdout, "studyfix-grade-stdout");
        Thread stderrReader = drain(process.getErrorStream(), stderr, "studyfix-grade-stderr");
        Thread inputWriter = null;
        if (stdin != null) {
            inputWriter = new Thread(() -> {
                try (OutputStream out = process.getOutputStream()) {
                    out.write(stdin);
                    out.flush();
                } catch (IOException ignored) { }
            }, "studyfix-grade-stdin");
            inputWriter.setDaemon(true);
            inputWriter.start();
        } else {
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
        }

        long limit = Math.max(1, timeoutMillis);
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limit);
        boolean timedOut = false;
        try {
            while (true) {
                if (stdout.exceeded()) {
                    if (containerName != null) killContainer(containerName);
                    process.destroyForcibly();
                    break;
                }
                long remaining = end - System.nanoTime();
                if (remaining <= 0) {
                    timedOut = true;
                    if (containerName != null) killContainer(containerName);
                    process.destroyForcibly();
                    break;
                }
                if (process.waitFor(Math.min(100, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining))), TimeUnit.MILLISECONDS)) break;
            }
            process.waitFor(3, TimeUnit.SECONDS);
            stdoutReader.join(1000);
            stderrReader.join(1000);
            if (inputWriter != null) {
                try { process.getOutputStream().close(); } catch (IOException ignored) { }
                inputWriter.join(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (containerName != null) killContainer(containerName);
            process.destroyForcibly();
            timedOut = true;
        }
        int exit = process.isAlive() ? -1 : process.exitValue();
        return new ProcessResult(exit, stdout.text(), stderr.text(), timedOut, stdout.exceeded(), null, elapsed(started));
    }

    private static Thread drain(InputStream input, BoundedCapture capture, String name) {
        Thread thread = new Thread(() -> {
            try (InputStream in = input) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) >= 0) capture.add(buffer, count);
            } catch (IOException ignored) { }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void killContainer(String name) {
        try {
            Process kill = new ProcessBuilder(docker, "kill", name).redirectErrorStream(true).start();
            kill.waitFor(2, TimeUnit.SECONDS);
            kill.destroyForcibly();
        } catch (Exception ignored) { }
    }

    private static Map<String, Object> testRow(Exercise.TestCase test, boolean passed,
                                                String message, long duration) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hidden", test.hidden());
        row.put("passed", passed);
        row.put("message", test.hidden() ? (passed ? "Hidden test đạt" : "Hidden test không đạt") : message);
        row.put("durationMs", duration);
        return row;
    }

    private static GradingResult unavailable(long start, String feedback, double maxScore, int total) {
        return result("SANDBOX_UNAVAILABLE", 0, maxScore, 0, total, safe(feedback), start, List.of());
    }

    private static GradingResult result(String verdict, double score, double maxScore, int passed,
                                        int total, String feedback, long started,
                                        List<Map<String, Object>> rows) {
        return new GradingResult(verdict, score, maxScore, passed, total, safe(feedback), elapsed(started), rows);
    }

    private static String normalize(String text) {
        String value = text.replace("\r\n", "\n").replace('\r', '\n');
        if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static int parseInt(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static long remainingMillis(long deadlineNanos) {
        return TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String containerName(String prefix) {
        return "studyfix-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String safe(String text) {
        if (text == null) return "";
        String filtered = text.replaceAll("(?i)([a-z]:\\\\[^\\r\\n ]+|/tmp/[^\\r\\n ]+)", "[đường dẫn ẩn]");
        return filtered.length() > 4000 ? filtered.substring(0, 4000) : filtered;
    }

    private record ProcessResult(int exitCode, String stdoutText, String stderrText, boolean timedOut,
                                 boolean outputExceeded, String startFailure, long elapsedMillis) {
        static ProcessResult failed(String message, long elapsed) {
            return new ProcessResult(-1, "", message, false, false, message, elapsed);
        }
    }

    private static final class BoundedCapture {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final AtomicLong seen = new AtomicLong();
        private final AtomicBoolean exceeded = new AtomicBoolean();
        BoundedCapture(int limit) { this.limit = limit; }
        synchronized void add(byte[] data, int length) {
            long prior = seen.getAndAdd(length);
            int keep = (int) Math.max(0, Math.min(length, limit - prior));
            if (keep > 0) bytes.write(data, 0, keep);
            if (prior + length > limit) exceeded.set(true);
        }
        boolean exceeded() { return exceeded.get(); }
        synchronized String text() { return bytes.toString(StandardCharsets.UTF_8); }
    }
}
