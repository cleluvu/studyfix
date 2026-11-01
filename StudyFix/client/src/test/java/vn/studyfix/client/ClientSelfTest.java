package vn.studyfix.client;

import vn.studyfix.client.net.FrameCodec;
import vn.studyfix.client.net.HistoryFrameAssembler;
import vn.studyfix.client.protocol.ServerMessage;
import vn.studyfix.client.protocol.ServerMessageParser;
import vn.studyfix.client.protocol.WireCommands;
import vn.studyfix.client.screen.ScreenShareWire;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Dependency-free smoke tests; run this main after compiling src/main/java + src/test/java. */
public final class ClientSelfTest {
    public static void main(String[] args) throws Exception {
        frameRoundTrip(); historyReassembly(); parserHandlesServerProtocol(); commandsPreserveCode(); screenPacketRoundTrip(); controlConnectionLoopback();
        System.out.println("StudyFix client self-tests: PASS");
    }
    private static void frameRoundTrip() throws Exception {
        String sample = "Xin chào StudyFix\nline 2 – tiếng Việt";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FrameCodec.write(new DataOutputStream(bytes), sample);
        eq(sample, FrameCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))), "TCP frame UTF-8");
    }
    private static void historyReassembly() {
        HistoryFrameAssembler assembler = new HistoryFrameAssembler();
        if (assembler.accept("[HISTORY_PART 1/2]\nXin chào ").isPresent()) fail("first history part must wait");
        eq("Xin chào StudyFix", assembler.accept("[HISTORY_PART 2/2]\nStudyFix").orElseThrow(), "history parts");
    }
    private static void parserHandlesServerProtocol() {
        ServerMessageParser parser = new ServerMessageParser();
        ServerMessage login = parser.parse("[LOGIN_OK] session=abc userId=user-1 role=TEACHER | Teacher One");
        eq(ServerMessage.Type.LOGIN_OK, login.type(), "login message type"); eq("abc", login.sessionId(), "session id");
        ServerMessage code = parser.parse("[Phòng 12] version=3, editor=Student One, token=9, hàng đợi=[Student Two]\n--- CODE CHUNG ĐÃ CẬP NHẬT ---\npublic class Main {}\n");
        eq(ServerMessage.Type.SHARED_CODE, code.type(), "code message type"); eq("12", code.roomId(), "room id"); eq(3L, code.version(), "version");
        eq("public class Main {}\n", code.code(), "code payload"); eq(List.of("Student Two"), code.queue(), "queue");
        ServerMessage committed = parser.parse("[COMMITTED] Đã lưu\n[Phòng 12] version=4, editor=không có, token=10, hàng đợi=[]\n--- CODE CHUNG ĐÃ CẬP NHẬT ---\nnew code\n");
        eq(4L, committed.version(), "state on secondary line");
        ServerMessage alreadyEditor = parser.parse("Bạn đang giữ quyền sửa. [Phòng 12] version=4, editor=Student One, token=10, hàng đợi=[]");
        eq(10L, alreadyEditor.token(), "state embedded in direct response");
        eq(ServerMessage.Type.CHAT, parser.parse("[Phòng 12] Student One: hello").type(), "chat message");
        ServerMessage members = parser.parse("Thành viên phòng 12: [session-a | Giang, session-b | Student Two]");
        eq(ServerMessage.Type.MEMBERS, members.type(), "member list message");
        if (members.memberNames().size() != 2 || !members.memberNames().get(0).contains("session-a")) fail("member snapshot should preserve session IDs");
        eq(ServerMessage.Type.PONG, parser.parse("/pong").type(), "heartbeat pong");
    }
    private static void commandsPreserveCode() {
        String code = "public class Main {\n    public static void main(String[] args) {}\n}\n";
        String command = WireCommands.update(4, 12, code);
        if (!command.endsWith(code)) fail("/update should preserve full multiline code payload");
        eq("/select 3 4 2,5,8", WireCommands.select(3, 4, "2,5,8"), "select command");
    }
    private static void controlConnectionLoopback() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            Thread fakeServer = new Thread(() -> {
                try (Socket peer = listener.accept()) {
                    String request = FrameCodec.read(new DataInputStream(peer.getInputStream()));
                    if (!request.startsWith("/login ")) throw new AssertionError("Expected login command: " + request);
                    FrameCodec.write(new DataOutputStream(peer.getOutputStream()), "[LOGIN_OK] session=test-session userId=test-user role=STUDENT | Student Test");
                    Thread.sleep(100);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            fakeServer.setDaemon(true); fakeServer.start();
            try (var connection = vn.studyfix.client.net.TcpControlConnection.connect("127.0.0.1", listener.getLocalPort(), 2000)) {
                String reply = connection.sendAndAwait("/login student1 Student123!", value -> value.startsWith("[LOGIN_OK]"), Duration.ofSeconds(3));
                if (!reply.contains("session=test-session")) fail("control connection should receive login reply");
            }
            fakeServer.join(1500);
        }
    }
    private static void screenPacketRoundTrip() throws Exception {
        byte[] jpeg = new byte[] {(byte)0xff, (byte)0xd8, 1, 2, (byte)0xff, (byte)0xd9};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ScreenShareWire.write(new DataOutputStream(bytes), ScreenShareWire.Type.FRAME, jpeg);
        ScreenShareWire.Packet packet = ScreenShareWire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        eq(ScreenShareWire.Type.FRAME, packet.type(), "screen packet type");
        if (!java.util.Arrays.equals(jpeg, packet.payload())) fail("screen payload should not be text-decoded");
    }
    private static void eq(Object expected, Object actual, String label) { if (!java.util.Objects.equals(expected, actual)) fail(label + ": expected " + expected + " got " + actual); }
    private static void fail(String text) { throw new AssertionError(text); }
}
