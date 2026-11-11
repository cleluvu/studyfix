package studyfix.assignment.grading;

import studyfix.assignment.model.Exercise;
import studyfix.assignment.model.GradingResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

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
    private static final long STARTUP_ALLOWANCE_MILLIS = 30_000;
    // The trusted supervisor retains only the capabilities required to drop the worker's
    // identity and stop it. Submitted code runs as a non-root worker with no effective capabilities.
    // Its environment never contains the random status token; it cannot signal/read the
    // root supervisor. Status therefore cannot be forged through submitted stderr.
    private static final String SUPERVISOR = """
            set -u
            token=$STUDYFIX_STATUS_TOKEN
            unset STUDYFIX_STATUS_TOKEN
            worker_uid=$STUDYFIX_WORKER_UID
            worker_gid=$STUDYFIX_WORKER_GID
            unset STUDYFIX_WORKER_UID STUDYFIX_WORKER_GID
            limit=$1
            shift
            started=$(/usr/bin/date +%s%N)
            /usr/bin/timeout --foreground --signal=TERM --kill-after=0.1s "$limit" \
              /usr/bin/setpriv --reuid="$worker_uid" --regid="$worker_gid" --clear-groups --no-new-privs --inh-caps=-all "$@"
            status=$?
            ended=$(/usr/bin/date +%s%N)
            printf '\\nSF_%s:%s:%s\\n' "$token" "$status" "$((ended-started))" >&2
            exit "$status"
            """;

    static long jobWallMillis() {
        return Math.max(60_000, Math.min(600_000,
                Long.getLong("studyfix.assignment.jobWallMillis", 300_000L)));
    }

    static Path createWorkspace(String prefix) throws IOException {
        // Keep the outer directory private on the host, even when a root-run Linux
        // service grants the container worker group access to the mounted child.
        return Files.createDirectory(Files.createTempDirectory(prefix).resolve("work"));
    }

    static void removeWorkspace(Path work) throws IOException {
        if (!work.getFileName().toString().equals("work") || !work.getParent().getFileName().toString().startsWith("studyfix-"))
            throw new IOException("Invalid sandbox workspace");
        try (var paths = Files.walk(work)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
        Files.deleteIfExists(work.getParent().resolve("supervisor.sh"));
        Files.deleteIfExists(work.getParent());
    }

    private static long[] workerIdentity(Path work) {
        try {
            if (!Files.getFileStore(work).supportsFileAttributeView("posix")) return new long[]{65534,65534};
            long uid=((Number)Files.getAttribute(work,"unix:uid")).longValue(),gid=((Number)Files.getAttribute(work,"unix:gid")).longValue();
            if(uid>0)return new long[]{uid,gid};
            try(var paths=Files.walk(work)){
                for(Path path:paths.toList()){
                    Files.setAttribute(path,"unix:gid",65534);
                    Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(Files.isDirectory(path)?"rwxrwx---":"rw-rw----"));
                }
            }
            return new long[]{65534,65534};
        }catch(IOException e){throw new IllegalStateException("Không chuẩn bị được quyền thư mục sandbox.",e);}
    }


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

        Path work = createWorkspace("studyfix-grade-");
        try {
            // Chỉ đưa các dữ liệu này vào container biên dịch và chạy bài nộp.
            Files.writeString(work.resolve("Main.java"), source, StandardCharsets.UTF_8);
            long deadlineNanos = started + TimeUnit.MILLISECONDS.toNanos(jobWallMillis());
            long executionRemaining = TimeUnit.SECONDS.toMillis(totalTimeoutSeconds);
            String compileName = containerName("compile");
            List<String> compileCmd = dockerBase(compileName, work, false);
            compileCmd.addAll(List.of("javac", "-J-Xmx64m", "-J-XX:ActiveProcessorCount=1", "--release", "17", "-proc:none", "-encoding", "UTF-8", "Main.java"));
            ProcessResult compile = runBudgeted(compileCmd, null, 30_000,
                    MAX_DIAGNOSTIC_BYTES, MAX_DIAGNOSTIC_BYTES, compileName, remainingMillis(deadlineNanos));
            if (compile.startFailure() != null) return unavailable(started, compile.startFailure(), maxScore, tests.size());
            if (compile.timedOut()) return result("TIME_LIMIT_EXCEEDED", 0, maxScore, 0, tests.size(),
                    "Biên dịch vượt quá thời gian giới hạn.", started, List.of());
            if (compile.exitCode() != 0) return result("COMPILE_ERROR", 0, maxScore, 0, tests.size(),
                    safe(compile.stderrText()), started, List.of());

            int totalWeight = tests.stream().mapToInt(Exercise.TestCase::weight).sum();
            int passed = 0;
            int earned = 0;
            boolean timeout = false, runtimeError = false, outputLimit = false, memoryLimit = false;
            List<Map<String, Object>> testResults = new ArrayList<>();

            for (int i = 0; i < tests.size(); i++) {
                Exercise.TestCase test = tests.get(i);
                long remaining = remainingMillis(deadlineNanos);
                if (remaining <= 0) {
                    return unavailable(started, "Docker vượt giới hạn thời gian vận hành của lượt chấm.", maxScore, tests.size());
                }
                if (executionRemaining <= 0) {
                    timeout = true;
                    testResults.add(testRow(test, false, "Chạm giới hạn tổng thời gian thực thi.", 0));
                    continue;
                }

                String runName = containerName("test");
                List<String> command = dockerBase(runName, work, true);
                command.addAll(List.of("java", "-Xmx64m", "-XX:ActiveProcessorCount=1", "-cp", "/code", "Main"));
                ProcessResult execution = runBudgeted(command,
                        test.input().getBytes(StandardCharsets.UTF_8),
                        Math.min(executionRemaining, TimeUnit.SECONDS.toMillis(perTestSeconds)),
                        MAX_STDOUT_BYTES, MAX_STDERR_BYTES, runName, remaining);
                executionRemaining -= execution.elapsedMillis();

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
                testResults.add(testRow(test, ok, message, execution.elapsedMillis(), actual));
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
            removeWorkspace(work);
        }
    }

    /** Compile-only for author templates, using the same isolation and bounded process capture. */
    public Map<String,Object> compileTemplate(List<Map<String,Object>> files) throws IOException {
        long started=System.nanoTime();Path work=createWorkspace("studyfix-template-");
        String name=containerName("template");
        try {
            studyfix.assignment.persistence.ProblemTemplate.validate(Map.of("files",files));
            Files.createDirectory(work.resolve("classes"));List<String> sources=new ArrayList<>();
            for(Map<String,Object> file:files){String path=(String)file.get("path");if(!"PUBLIC".equals(file.get("visibility"))||!path.endsWith(".java"))continue;Path target=work.resolve(path).normalize();if(!target.startsWith(work))throw new IllegalArgumentException("Unsafe template path");Files.createDirectories(target.getParent());Files.writeString(target,(String)file.get("content"),StandardCharsets.UTF_8);sources.add("./"+path);}
            if(sources.isEmpty())return Map.of("verdict","INVALID_TEMPLATE","diagnostic","Template chưa có file Java công khai.","durationMillis",elapsed(started));
              List<String> command=dockerBase(name,work,false);command.addAll(List.of("javac","-J-Xmx64m","-J-XX:ActiveProcessorCount=1","--release","17","-proc:none","-encoding","UTF-8","-d","classes"));command.addAll(sources);
            ProcessResult result=runBudgeted(command,null,30000,MAX_DIAGNOSTIC_BYTES,MAX_DIAGNOSTIC_BYTES,name,jobWallMillis());
            String verdict=result.startFailure()!=null||result.exitCode()==125||result.exitCode()==126||result.exitCode()==127?"SANDBOX_UNAVAILABLE":result.timedOut()?"TIME_LIMIT_EXCEEDED":result.outputExceeded()?"OUTPUT_LIMIT_EXCEEDED":result.exitCode()==0?"COMPILED":"COMPILE_ERROR";
            return Map.of("verdict",verdict,"diagnostic",safe(result.startFailure()!=null?result.startFailure():result.stderrText()+result.stdoutText()),"durationMillis",result.elapsedMillis(),"wallDurationMillis",elapsed(started),"image",image);
        } finally {
            killContainer(name);
            removeWorkspace(work);
        }
    }

    List<String> dockerBase(String name, Path work, boolean readOnlyCode) { return dockerBase(name,work,readOnlyCode,128); }
    List<String> dockerBase(String name,Path work,boolean readOnlyCode,int memory) {
        long[] worker=workerIdentity(work);
        Path supervisor=work.getParent().resolve("supervisor.sh");
        try {Files.writeString(supervisor,SUPERVISOR,StandardCharsets.UTF_8);}
        catch(IOException e){throw new IllegalStateException("Không chuẩn bị được bộ giám sát sandbox.",e);}
        List<String> command = new ArrayList<>(List.of(
                docker, "run", "--pull=never", "--rm", "--name", name,
                "--network", "none", "--memory="+memory+"m", "--memory-swap="+memory+"m", "--cpus=1",
                "--pids-limit=32", "--read-only", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "--tmpfs", "/tmp:rw,exec,nosuid,size=16m",
                "--mount", "type=bind,source="+supervisor.toAbsolutePath()+",target=/judge-supervisor.sh,readonly",
                "--env", "STUDYFIX_WORKER_UID="+worker[0], "--env", "STUDYFIX_WORKER_GID="+worker[1],
                "--mount", "type=bind,source=" + work.toAbsolutePath() + ",target=" + (readOnlyCode ? "/code,readonly" : "/work")));
        if (readOnlyCode) command.addAll(List.of("-i", "-w", "/tmp", image));
        else command.addAll(List.of("-w", "/work", image));
        return command;
    }

    ProcessResult runBudgeted(List<String> command, byte[] stdin, long executionMillis,
                             int stdoutLimit, int stderrLimit, String name, long wallRemaining) {
        if (wallRemaining <= 0) return ProcessResult.failed("Đã hết thời gian vận hành Docker của lượt chấm.", 0);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        int imageAt = command.indexOf(image);
        if (imageAt < 0) throw new IllegalArgumentException("Missing sandbox image");
        List<String> supervised = new ArrayList<>(command.subList(0, imageAt));
        supervised.addAll(List.of("--cap-add=SETUID", "--cap-add=SETGID", "--cap-add=KILL",
                "--env", "STUDYFIX_STATUS_TOKEN=" + token, image, "sh", "/judge-supervisor.sh", String.format(Locale.ROOT, "%.3fs", Math.max(1, executionMillis)/1000d)));
        supervised.addAll(command.subList(imageAt + 1, command.size()));
        ProcessResult raw = runProcess(supervised, stdin,
                Math.min(wallRemaining, STARTUP_ALLOWANCE_MILLIS + executionMillis), stdoutLimit, stderrLimit + 256, name);
        if (raw.outputExceeded()) return raw;
        if (raw.startFailure() != null || raw.timedOut()) return ProcessResult.failed(
                "Docker không khởi động/hoàn tất kết nối trong thời gian vận hành; đây không phải lỗi quá giờ của bài làm. "
                        + safe(raw.startFailure() != null ? raw.startFailure() : raw.stderrText()), raw.elapsedMillis());
        var status = Pattern.compile("(?m)^SF_" + token + ":(\\d+):(\\d+)\\r?$").matcher(raw.stderrText());
        if (!status.find()) return ProcessResult.failed("Không nhận được trạng thái từ bộ giám sát Docker. "
                + safe(raw.stderrText()), raw.elapsedMillis());
        int exit = Integer.parseInt(status.group(1));
        long duration = TimeUnit.NANOSECONDS.toMillis(Long.parseLong(status.group(2)));
        String diagnostic = status.replaceFirst("").strip();
        if (raw.exitCode() != exit || Set.of(125,126,127).contains(exit)) return ProcessResult.failed(
                "Môi trường thực thi Docker chưa sẵn sàng. " + safe(diagnostic), duration);
        boolean timeout = (exit == 124 || exit == 137) && duration >= executionMillis;
        return new ProcessResult(exit, raw.stdoutText(), diagnostic, timeout,
                diagnostic.getBytes(StandardCharsets.UTF_8).length > stderrLimit, null, duration);
    }

    ProcessResult runProcess(List<String> command, byte[] stdin, long timeoutMillis,
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
                if (stdout.exceeded() || stderr.exceeded()) {
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
        return new ProcessResult(exit, stdout.text(), stderr.text(), timedOut, stdout.exceeded() || stderr.exceeded(), null, elapsed(started));
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

    void killContainer(String name) {
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
        if(!test.hidden()) { row.put("input",safe(test.input())); row.put("expectedOutput",safe(test.expected())); }
        return row;
    }

    private static Map<String,Object> testRow(Exercise.TestCase test,boolean passed,String message,long duration,String actual) {
        Map<String,Object> row=testRow(test,passed,message,duration);
        if(!test.hidden())row.put("actualOutput",safe(actual));
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

    static String containerName(String prefix) {
        return "studyfix-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String safe(String text) {
        if (text == null) return "";
        String filtered = text.replaceAll("(?i)([a-z]:\\\\[^\\r\\n ]+|/tmp/[^\\r\\n ]+)", "[đường dẫn ẩn]");
        return filtered.length() > 4000 ? filtered.substring(0, 4000) : filtered;
    }

    record ProcessResult(int exitCode, String stdoutText, String stderrText, boolean timedOut,
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
