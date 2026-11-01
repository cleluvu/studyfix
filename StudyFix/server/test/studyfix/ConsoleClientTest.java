package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Launches the actual console client against a labelled fake server (integration stub). */
public final class ConsoleClientTest {
    private static String javaExecutable() {
        String exe = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }
    public static void main(String[] args) throws Exception {
        Path source = Files.createTempFile(Path.of("build"), "code có dấu ", ".java");
        String code = "class Main { // Xin chào\n}\n";
        Files.writeString(source, code);
        Path consoleOutput = Files.createTempFile(Path.of("build"), "console-output-", ".txt");
        String longHistory = "BEGIN_HISTORY\n" + "Tiếng Việt 😀\r\n".repeat(10000) + "END_HISTORY";
        try (ServerSocket fake = new ServerSocket(0)) {
            String java = javaExecutable();
            Process client = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-Dstudyfix.port=" + fake.getLocalPort(),
                    "-cp", System.getProperty("java.class.path"), "studyfix.Client")
                    .redirectErrorStream(true).redirectOutput(consoleOutput.toFile()).start();
            try {
                BufferedWriter keyboard = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
                keyboard.write("127.0.0.1\nstudent1\nStudent123!\n"); keyboard.flush();
                fake.setSoTimeout(8000);
                try (Socket socket = fake.accept()) {
                    socket.setSoTimeout(8000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    if (!Protocol.receive(in).equals("/login student1 Student123!")) throw new AssertionError("Client login mismatch");
                    Protocol.send(out, "[LOGIN_OK] session=stub userId=stub role=STUDENT");
                    for (String part : Protocol.historyFrames(longHistory)) Protocol.send(out, part);
                    // Quiet multipart rendering is a separate case from concurrent local output.
                    // ConsoleHistoryInterleaveTest deterministically covers the latter regression.
                    long renderedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    while (!Files.readString(consoleOutput, StandardCharsets.UTF_8).contains("END_HISTORY")
                            && System.nanoTime() < renderedDeadline) Thread.sleep(10);
                    keyboard.write("/heartbeat off\n/load 0 1 " + source.toAbsolutePath() + "\n"); keyboard.flush();
                    String update;
                    do { update = Protocol.receive(in); if (update.equals("/ping")) Protocol.send(out, "/pong"); }
                    while (update.equals("/ping"));
                    if (!update.equals("/update 0 1 " + code)) throw new AssertionError("/load lost UTF-8/newlines/path with spaces");
                    System.out.println("PASS actual console /load UTF-8, multiline, path with spaces");
                    keyboard.write("/rangefile 7 " + source.toAbsolutePath() + "\n"); keyboard.flush();
                    String range;
                    do { range = Protocol.receive(in); if (range.equals("/ping")) Protocol.send(out, "/pong"); }
                    while (range.equals("/ping"));
                    if (!range.equals("/setrange 7 " + code)) throw new AssertionError("/rangefile did not preserve exact file content");
                    System.out.println("PASS actual console /rangefile -> /setrange exact UTF-8 multiline path with spaces");
                    socket.setSoTimeout(1500);
                    try { Protocol.receive(in); throw new AssertionError("Heartbeat off still sends"); }
                    catch (SocketTimeoutException expected) { System.out.println("PASS actual console /heartbeat off"); }
                    keyboard.write("/heartbeat on\n"); keyboard.flush();
                    socket.setSoTimeout(7000);
                    if (!Protocol.receive(in).equals("/ping")) throw new AssertionError("Heartbeat on missing ping");
                    System.out.println("PASS actual console /heartbeat on; stub now withholds pong for 20 seconds");
                    if (!client.waitFor(25000, TimeUnit.MILLISECONDS)) throw new AssertionError("Client watchdog did not exit");
                    String output = Files.readString(consoleOutput, StandardCharsets.UTF_8);
                    if (!output.contains(longHistory) || output.contains("[HISTORY_PART "))
                        throw new AssertionError("Client lost/reordered Unicode history or added newlines between parts");
                    System.out.println("PASS actual console multipart Unicode history displayed exactly without part headers");
                    if (client.exitValue() != 0 || !output.contains("Server không phản hồi pong trong 20 giây.") || output.contains(": null"))
                        throw new AssertionError(output);
                    System.out.println("PASS actual client pong deadline, clear disconnect reason, heartbeat executor shutdown");
                }
            } finally { if (client.isAlive()) client.destroyForcibly().waitFor(); }
        }
    }
}
