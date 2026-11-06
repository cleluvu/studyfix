package studyfix;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;

public class Client {
    /** Never hold this monitor while waiting for the socket or keyboard. */
    private static final class ConsoleOutput {
        private final PrintStream out;
        private final java.util.Deque<String> pending = new java.util.ArrayDeque<>();
        private int nextPart;
        private int totalParts;

        ConsoleOutput(PrintStream out) { this.out = out; }

        synchronized void println(String text) {
            if (nextPart != 0) pending.addLast(text);
            else out.println(text);
        }

        synchronized void historyPart(String frame) throws IOException {
            int end = frame.indexOf("]\n");
            int index, count;
            try {
                if (end < 0) throw new IllegalArgumentException();
                String[] position = frame.substring(14, end).split("/", -1);
                if (position.length != 2) throw new IllegalArgumentException();
                index = Integer.parseInt(position[0]); count = Integer.parseInt(position[1]);
                if (index < 1 || count < 1 || index > count
                        || (nextPart == 0 ? index != 1 : index != nextPart || count != totalParts))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw new IOException("Thứ tự các phần lịch sử không hợp lệ.", e);
            }
            totalParts = count;
            nextPart = index + 1;
            out.print(frame.substring(end + 2));
            if (index == count) {
                out.println();
                nextPart = 0;
                flushPending();
            }
        }

        synchronized void finish() {
            if (nextPart != 0) {
                out.println();
                out.println("[Lịch sử chưa nhận đủ; kết nối đã kết thúc.]");
                nextPart = 0;
            }
            flushPending();
        }

        private void flushPending() {
            while (!pending.isEmpty()) out.println(pending.removeFirst());
        }
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        ConsoleOutput console = new ConsoleOutput(System.out);
        AtomicBoolean quitting = new AtomicBoolean(false);
        // Chỉ dùng một Scanner cho cả phần nhập thông tin và nhập lệnh.
        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.print("Nhập IP server, Enter để dùng 127.0.0.1: ");
        if (!scanner.hasNextLine()) { return; }
        String serverIp = scanner.nextLine().trim();
        if (serverIp.isEmpty()) { serverIp = "127.0.0.1"; }

        System.out.print("Username (teacher / student1 / student2): ");
        if (!scanner.hasNextLine()) return;
        String username = scanner.nextLine().trim();
        System.out.print("Password (console test, có thể hiện ký tự nhập): ");
        if (!scanner.hasNextLine()) return;
        String password = scanner.nextLine();
        
        try (Socket socket = new Socket(); ) {
            int port = Integer.getInteger("studyfix.port", 5000);
            socket.connect(new InetSocketAddress(serverIp, port), 5000);
            // Không đặt timeout đọc 5 giây: chat phải chờ được khi phòng chưa có tin.
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            // Đăng ký tên trước khi nhận lệnh; chỉ giới hạn thời gian bắt tay.
            socket.setSoTimeout(5000);
            Protocol.send(out, "/login " + username + " " + password);
            password = null;
            String greeting = Protocol.receive(in);
            console.println(greeting);
            if (!greeting.startsWith("[LOGIN_OK]")) {
                console.println("Đăng nhập thất bại. Chạy lại client và kiểm tra tài khoản.");
                return;
            }
            socket.setSoTimeout(0);
            console.println("Đã kết nối tới StudyFix Server " + serverIp + ":" + port);
            console.println("/create | /join mã_phòng | /members | /leave | /chat nội_dung | /code | /lines | /history [version] | /request | /release token | /select version token 2,5,8 hoặc 4-7 | /setline token dòng nội_dung | /delete token dòng | /keep token dòng | /rangefile token đường_dẫn | /deleterange token | /keeprange token | /draft token | /finish token | /cancel token | /load version token đường_dẫn (chủ phòng) | /quit");

            AtomicBoolean heartbeatEnabled = new AtomicBoolean(true);
            AtomicLong lastPong = new AtomicLong(System.nanoTime());
            AtomicReference<String> endedReason = new AtomicReference<>();
            ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "client-heartbeat");
                t.setDaemon(true);
                return t;
            });
            heartbeat.scheduleAtFixedRate(() -> {
                if (socket.isClosed() || !heartbeatEnabled.get()) { return; }
                try {
                    Protocol.send(out, "/ping");
                } catch (IOException e) {
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }, 0, 5, TimeUnit.SECONDS);
            heartbeat.scheduleAtFixedRate(() -> {
                if (heartbeatEnabled.get() && System.nanoTime() - lastPong.get() >= TimeUnit.SECONDS.toNanos(20)) {
                    endedReason.compareAndSet(null, "Server không phản hồi pong trong 20 giây.");
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }, 1, 1, TimeUnit.SECONDS);

            // Luồng này chỉ đọc bàn phím và gửi. Không đọc dữ liệu từ socket.
            Thread keyboard = new Thread(() -> {
                try {
                    while (!socket.isClosed() && scanner.hasNextLine()) {
                        String message = scanner.nextLine();
                        if (message.trim().equals("/heartbeat off")) {
                            heartbeatEnabled.set(false);
                            console.println("Đã tạm dừng heartbeat để test. Server sẽ đóng kết nối nếu hết hạn.");
                            continue;
                        }
                        if (message.trim().equals("/heartbeat on")) {
                            lastPong.set(System.nanoTime());
                            heartbeatEnabled.set(true);
                            console.println("Đã bật lại heartbeat.");
                            continue;
                        }
                        if (message.trim().equals("/quit")) {
                            quitting.set(true);
                            break;
                        }
                        if (message.getBytes(StandardCharsets.UTF_8).length > Protocol.MAX_BYTES) {
                            console.println("Nội dung quá dài, chưa gửi.");
                            continue;
                        }
                        if (message.startsWith("/rangefile ")) {
                            String[] parts = message.split("\\s+", 3);
                            try {
                                if (parts.length != 3) throw new IllegalArgumentException("/rangefile token đường_dẫn");
                                Long.parseLong(parts[1]); Path path = Path.of(parts[2]);
                                if (Files.size(path) > 32000) throw new IllegalArgumentException("File vượt 32000 byte.");
                                message = "/setrange " + parts[1] + " " + Files.readString(path, StandardCharsets.UTF_8);
                            } catch (IOException | IllegalArgumentException e) {
                                console.println("Không đọc được đoạn code: " + e.getMessage()); continue;
                            }
                        }
                        if (message.startsWith("/load ")) {
                            String[] parts = message.split("\\s+", 4);
                            if (parts.length != 4) {
                                console.println("Cách dùng: /load version token đường_dẫn_file_java");
                                continue;
                            }
                            try {
                                Long.parseLong(parts[1]); Long.parseLong(parts[2]);
                                Path path = Path.of(parts[3]);
                                if (Files.size(path) > 32000) {
                                    console.println("File vượt 32000 byte."); continue;
                                }
                                message = "/update " + parts[1] + " " + parts[2] + " "
                                        + Files.readString(path, StandardCharsets.UTF_8);
                            } catch (IOException | IllegalArgumentException e) {
                                console.println("Không đọc được file: " + e.getMessage()); continue;
                            }
                        }
                        if (message.getBytes(StandardCharsets.UTF_8).length > Protocol.MAX_BYTES) {
                            console.println("Nội dung quá dài, chưa gửi.");
                            continue;
                        }
                        Protocol.send(out, message);
                    }
                } catch (IOException e) {
                    if (!quitting.get()) { console.println("Không gửi được: " + e.getMessage()); }
                } finally {
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }, "keyboard-sender");
            keyboard.setDaemon(true);
            keyboard.start();

            // Luồng main chỉ nhận: tin xuất hiện ngay cả khi người dùng chưa nhập gì.
            try {
                while (true) {
                    String response = Protocol.receive(in);
                    if (response.equals("/pong")) lastPong.set(System.nanoTime());
                    else if (response.startsWith("[HISTORY_PART ")) console.historyPart(response);
                    else { console.println(response); }
                }
            } catch (IOException e) {
                if (!quitting.get()) {
                    String reason = endedReason.get();
                    if (reason == null) reason = e instanceof EOFException
                            ? "Server đã đóng kết nối." : "Mất kết nối với server (" + e.getClass().getSimpleName() + ").";
                    console.println(reason);
                }
            } finally {
                console.finish();
                heartbeat.shutdownNow();
            }
        } catch (IOException e) {
            console.println("Không kết nối được tới server: " + e.getMessage());
        }
        console.println("Client đã kết thúc.");
    }
}
