package studyfix;

import studyfix.screenshare.ScreenShareWire;
import studyfix.screenshare.ScreenShareWire.Packet;
import studyfix.screenshare.ScreenShareWire.Type;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** End-to-end screen-share tests using real control TCP, relay TCP and SQLite on loopback. */
public final class ScreenShareRelayIntegrationTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }

    private static final Pattern TICKET = Pattern.compile(
            "^\\[SCREEN_TICKET]\\s+mode=(PUBLISH|VIEW)\\s+room=(\\S+)\\s+port=(\\d+)\\s+ticket=(\\S+)$");

    private record Ticket(String mode, String room, int port, String value) { }

    private static final class ControlPeer implements AutoCloseable {
        private final Socket socket = new Socket();
        private final DataOutputStream out;
        private final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        private final Thread reader;
        final String username;
        final String session;

        ControlPeer(int port, String username, String password) throws Exception {
            this.username = username;
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setTcpNoDelay(true);
            out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            reader = new Thread(() -> {
                try {
                    while (!socket.isClosed()) inbox.put(Protocol.receive(in));
                } catch (Exception ignored) {
                    inbox.offer("[CONTROL_CLOSED]");
                }
            }, "screen-share-test-control-reader-" + username);
            reader.setDaemon(true);
            reader.start();
            send("/login " + username + " " + password);
            String login = await("[LOGIN_OK]");
            Matcher m = Pattern.compile("session=(\\S+?)\\s+userId=").matcher(login);
            if (!m.find()) throw new AssertionError("Cannot parse session from login response: " + login);
            session = m.group(1);
        }

        void send(String command) throws IOException { Protocol.send(out, command); }

        String await(String marker) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            String last = "";
            while (System.nanoTime() < until) {
                String value = inbox.poll(200, TimeUnit.MILLISECONDS);
                if (value == null) continue;
                last = value;
                if (value.contains(marker)) return value;
                if (value.equals("[CONTROL_CLOSED]")) break;
            }
            throw new AssertionError("Did not receive '" + marker + "' for " + username + "; last=" + last);
        }

        Ticket requestTicket(String mode) throws Exception {
            send("/screen-ticket " + mode.toLowerCase());
            String response = await("[SCREEN_TICKET]");
            Matcher m = TICKET.matcher(response);
            if (!m.matches()) throw new AssertionError("Malformed screen ticket response: " + response);
            Ticket ticket = new Ticket(m.group(1), m.group(2), Integer.parseInt(m.group(3)), m.group(4));
            check(ticket.mode().equals(mode), username + " receives " + mode + " ticket");
            return ticket;
        }

        @Override public void close() throws IOException {
            try { send("/quit"); } catch (IOException ignored) { }
            socket.close();
        }
    }

    private static final class RelayPeer implements AutoCloseable {
        final Socket socket = new Socket();
        final DataInputStream in;
        final DataOutputStream out;
        final Packet greeting;

        RelayPeer(int port, String ticket) throws Exception {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(5000);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            ScreenShareWire.write(out, Type.HELLO, ScreenShareWire.utf8(ticket));
            greeting = ScreenShareWire.read(in);
        }

        void send(Type type, byte[] payload) throws IOException { ScreenShareWire.write(out, type, payload); }
        Packet receive() throws IOException { return ScreenShareWire.read(in); }
        @Override public void close() throws IOException { socket.close(); }
    }

    public static void main(String[] args) throws Exception {
        String oldScreenPort = System.getProperty("studyfix.screen.port");
        System.setProperty("studyfix.screen.port", "0"); // ask OS for a free test port
        Files.createDirectories(Path.of("build"));
        Path temp = Files.createTempDirectory(Path.of("build").toAbsolutePath(), "screen-share-integration-");
        Path database = temp.resolve("studyfix.db");
        Server server = new Server(0, database);
        server.start();
        try (server;
             ControlPeer teacher = new ControlPeer(server.port(), "teacher", "Teacher123!");
             ControlPeer student1 = new ControlPeer(server.port(), "student1", "Student123!");
             ControlPeer student2 = new ControlPeer(server.port(), "student2", "Student234!");
             ControlPeer teacher2 = new ControlPeer(server.port(), "teacher2", "Teacher234!")) {

            teacher2.send("/screen-ticket view");
            check(teacher2.await("Bạn chưa tham gia phòng.").contains("Bạn chưa tham gia phòng"),
                    "Deny ticket request when session is not in a room");

            teacher.send("/create");
            String created = teacher.await("Đã tạo và tham gia phòng:");
            String roomId = created.substring(created.lastIndexOf(':') + 1).trim();
            check(!roomId.isEmpty(), "Teacher creates a room for screen-share test");
            teacher.await("--- CODE CHUNG ---");

            student1.send("/join " + roomId);
            student1.await("Đã tham gia phòng:");
            student1.await("--- CODE CHUNG ---");
            student2.send("/join " + roomId);
            student2.await("Đã tham gia phòng:");
            student2.await("--- CODE CHUNG ---");

            Ticket publish = teacher.requestTicket("PUBLISH");
            Ticket view = student1.requestTicket("VIEW");
            Ticket competingPublish = student2.requestTicket("PUBLISH");
            check(publish.room().equals(roomId) && view.room().equals(roomId),
                    "Tickets are scoped to the current room");
            check(publish.port() == server.screenSharePort() && view.port() == server.screenSharePort(),
                    "Control response advertises the actual relay port");

            try (RelayPeer publisher = new RelayPeer(publish.port(), publish.value());
                 RelayPeer viewer = new RelayPeer(view.port(), view.value())) {
                check(publisher.greeting.type() == Type.STATUS
                                && ScreenShareWire.fromUtf8(publisher.greeting.payload()).contains("OK PUBLISH"),
                        "Publisher ticket handshake accepted");
                check(viewer.greeting.type() == Type.STATUS
                                && ScreenShareWire.fromUtf8(viewer.greeting.payload()).contains("OK VIEW"),
                        "Viewer ticket handshake accepted");

                ByteArrayOutputStream jpegBuffer = new ByteArrayOutputStream();
                BufferedImage fixtureImage = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
                fixtureImage.setRGB(1, 1, 0x00ff00);
                check(ImageIO.write(fixtureImage, "jpg", jpegBuffer), "Create a real JPEG frame fixture");
                byte[] jpegSample = jpegBuffer.toByteArray();
                publisher.send(Type.FRAME, jpegSample);
                Packet received = viewer.receive();
                check(received.type() == Type.FRAME && Arrays.equals(received.payload(), jpegSample),
                        "Publisher JPEG frame is relayed to viewer in the same room");
                check(ImageIO.read(new java.io.ByteArrayInputStream(received.payload())) != null,
                        "Relayed JPEG payload can be decoded as an image");

                try (RelayPeer rejectedPublisher = new RelayPeer(competingPublish.port(), competingPublish.value())) {
                    check(rejectedPublisher.greeting.type() == Type.ERROR
                                    && ScreenShareWire.fromUtf8(rejectedPublisher.greeting.payload()).contains("publisher is already active"),
                            "Only one publisher is accepted per room");
                }

                try (RelayPeer replayedTicket = new RelayPeer(view.port(), view.value())) {
                    check(replayedTicket.greeting.type() == Type.ERROR
                                    && ScreenShareWire.fromUtf8(replayedTicket.greeting.payload()).contains("Ticket missing or expired"),
                            "One-time ticket cannot be reused");
                }

                publisher.send(Type.STOP, new byte[0]);
                Packet stopped = viewer.receive();
                check(stopped.type() == Type.STOP,
                        "Publisher stop closes viewer stream with STOP packet");
            }

            // Verify the opposite role direction too: a student can publish and the teacher can view.
            Ticket studentPublish = student1.requestTicket("PUBLISH");
            Ticket teacherView = teacher.requestTicket("VIEW");
            try (RelayPeer studentPublisher = new RelayPeer(studentPublish.port(), studentPublish.value());
                 RelayPeer teacherViewer = new RelayPeer(teacherView.port(), teacherView.value())) {
                check(studentPublisher.greeting.type() == Type.STATUS
                                && ScreenShareWire.fromUtf8(studentPublisher.greeting.payload()).contains("OK PUBLISH"),
                        "Student can publish a screen-share stream");
                check(teacherViewer.greeting.type() == Type.STATUS
                                && ScreenShareWire.fromUtf8(teacherViewer.greeting.payload()).contains("OK VIEW"),
                        "Teacher can receive a student screen-share stream");
                ByteArrayOutputStream studentFrameBuffer = new ByteArrayOutputStream();
                BufferedImage studentFixtureImage = new BufferedImage(5, 4, BufferedImage.TYPE_INT_RGB);
                studentFixtureImage.setRGB(2, 2, 0x0000ff);
                check(ImageIO.write(studentFixtureImage, "jpg", studentFrameBuffer),
                        "Create a JPEG frame for the student publisher");
                byte[] studentJpeg = studentFrameBuffer.toByteArray();
                studentPublisher.send(Type.FRAME, studentJpeg);
                Packet teacherReceived = teacherViewer.receive();
                check(teacherReceived.type() == Type.FRAME && Arrays.equals(teacherReceived.payload(), studentJpeg),
                        "Student publisher frame is relayed to teacher viewer in the same room");
                check(ImageIO.read(new java.io.ByteArrayInputStream(teacherReceived.payload())) != null,
                        "Teacher can decode the relayed student JPEG frame");
                studentPublisher.send(Type.STOP, new byte[0]);
                check(teacherViewer.receive().type() == Type.STOP,
                        "Student stopping screen share closes teacher viewer stream");
            }

            // Verify that leaving the control room also tears down an active screen stream.
            Ticket publishOnDeparture = teacher.requestTicket("PUBLISH");
            Ticket viewOnDeparture = student1.requestTicket("VIEW");
            try (RelayPeer publisher = new RelayPeer(publishOnDeparture.port(), publishOnDeparture.value());
                 RelayPeer viewer = new RelayPeer(viewOnDeparture.port(), viewOnDeparture.value())) {
                check(publisher.greeting.type() == Type.STATUS && viewer.greeting.type() == Type.STATUS,
                        "Second share session starts before departure test");
                teacher.send("/leave");
                teacher.await("Đã rời phòng:");
                Packet stoppedOnLeave = viewer.receive();
                check(stoppedOnLeave.type() == Type.STOP,
                        "Leaving the room closes publisher and viewer streams");
            }

            System.out.println("ALL PASSED: " + checks + " screen-share integration checks (loopback TCP + SQLite)");
        } finally {
            if (oldScreenPort == null) System.clearProperty("studyfix.screen.port");
            else System.setProperty("studyfix.screen.port", oldScreenPort);
        }
    }
}
