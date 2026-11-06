package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Deterministic regression: local notices must wait until the multipart display ends. */
public final class ConsoleHistoryInterleaveTest {
    static void waitForText(Path log,String value) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            if(Files.readString(log).contains(value)) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Missing console marker: "+value);
    }
    public static void main(String[] args) throws Exception {
        Path log=Files.createTempFile(Path.of("build"),"console-interleave-",".log");
        System.out.println("CONSOLE_EVIDENCE="+log.toAbsolutePath());
        String history="BEGIN_HISTORY\n"+"Tiếng Việt 😀\r\n".repeat(10000)+"END_HISTORY";
        var frames=Protocol.historyFrames(history);
        boolean intact;
        try(ServerSocket fake=new ServerSocket(0)) {
            Process client=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstudyfix.port="+fake.getLocalPort(),
                    "-cp",System.getProperty("java.class.path"),"studyfix.Client")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                Writer keys=new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8);
                keys.write("127.0.0.1\nstudent1\nStudent123!\n"); keys.flush();
                fake.setSoTimeout(5000);
                try(Socket socket=fake.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    String login=Protocol.receive(in);
                    if(!login.startsWith("/login ")) throw new AssertionError("Missing login");
                    Protocol.send(out,"[LOGIN_OK] session=stub userId=stub role=STUDENT");
                    Protocol.send(out,frames.getFirst());
                    waitForText(log,"BEGIN_HISTORY");
                    keys.write("/heartbeat off\n/heartbeat on\n/load invalid\n/chat KEYBOARD_ACK\n"); keys.flush();
                    // An actual outbound command proves keyboard actions completed. Do not wait
                    // for their notices: the corrected client defers those until history ends.
                    for (;;) {
                        String command=Protocol.receive(in);
                        if(command.equals("/chat KEYBOARD_ACK")) break;
                        if(command.equals("/ping")) Protocol.send(out,"/pong");
                    }
                    for(int i=1;i<frames.size();i++) Protocol.send(out,frames.get(i));
                    waitForText(log,"END_HISTORY");
                    waitForText(log,"Đã tạm dừng heartbeat");
                    waitForText(log,"Đã bật lại heartbeat");
                    waitForText(log,"Cách dùng: /load version token đường_dẫn_file_java");
                    String output=Files.readString(log);
                    int end=output.indexOf("END_HISTORY");
                    intact=output.contains(history)
                            && output.indexOf("Đã tạm dừng heartbeat")>end
                            && output.indexOf("Đã bật lại heartbeat")>end
                            && output.indexOf("Cách dùng: /load version token đường_dẫn_file_java")>end;
                    keys.write("/quit\n"); keys.flush();
                    if(!client.waitFor(5,TimeUnit.SECONDS)) throw new AssertionError("Client did not quit");
                }
            } finally {if(client.isAlive()) client.destroyForcibly().waitFor();}
        }
        System.out.println("METRIC frames="+frames.size()+" history_utf8_bytes="+history.getBytes(StandardCharsets.UTF_8).length);
        System.out.println((intact?"PASS":"FAIL")+" multipart history stays contiguous while keyboard prints heartbeat status");
        if(!intact) throw new AssertionError("Local keyboard notice inserted between multipart history frames; see evidence log");
        interruptedHistory(frames.getFirst());
    }

    static void interruptedHistory(String first) throws Exception {
        Path log=Files.createTempFile(Path.of("build"),"console-history-abort-",".log");
        try(ServerSocket fake=new ServerSocket(0)) {
            Process client=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                    "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstudyfix.port="+fake.getLocalPort(),
                    "-cp",System.getProperty("java.class.path"),"studyfix.Client")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                Writer keys=new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8);
                keys.write("127.0.0.1\nstudent1\nStudent123!\n"); keys.flush(); fake.setSoTimeout(5000);
                try(Socket socket=fake.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    if(!Protocol.receive(in).startsWith("/login ")) throw new AssertionError("Missing login");
                    Protocol.send(out,"[LOGIN_OK] session=stub role=STUDENT"); Protocol.send(out,first);
                    waitForText(log,"BEGIN_HISTORY"); keys.write("/heartbeat off\n/chat ABORT_ACK\n"); keys.flush();
                    for(;;) {String cmd=Protocol.receive(in);if(cmd.equals("/chat ABORT_ACK"))break;if(cmd.equals("/ping"))Protocol.send(out,"/pong");}
                    socket.shutdownOutput();
                    if(!client.waitFor(5,TimeUnit.SECONDS)) throw new AssertionError("Client stuck during incomplete history");
                    String output=Files.readString(log);
                    if(client.exitValue()!=0 || !output.contains("Lịch sử chưa nhận đủ") || !output.contains("Đã tạm dừng heartbeat")
                            || !output.contains("Server đã đóng kết nối.") || output.contains("END_HISTORY")) throw new AssertionError("Incomplete history/queued notice not reported correctly");
                }
            } finally {if(client.isAlive())client.destroyForcibly().waitFor();}
        }
        System.out.println("PASS incomplete history flushes deferred notices and client exits on EOF");
        System.out.println("ABORT_EVIDENCE="+log.toAbsolutePath());
    }
}
