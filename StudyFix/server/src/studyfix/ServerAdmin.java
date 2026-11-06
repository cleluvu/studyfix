package studyfix;

import studyfix.assignment.AssignmentModuleConfig;
import studyfix.assignment.service.AssignmentService;
import studyfix.assignment.transport.AssignmentTcpServer;

import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Giao diện quản lý server bằng Swing. Đọc/ghi dữ liệu và lấy bản chụp trạng thái chạy ngoài luồng giao diện để tránh bị đơ. */
public final class ServerAdmin {
    private final JFrame frame = new JFrame("StudyFix — Server Manager");
    private final JLabel status = new JLabel("Đang khởi động StudyFix…");
    private final JLabel serviceStatus = new JLabel("Control TCP 5000 · Screen TCP 5001 · Assignment TCP 5002 · Web HTTP/HTTPS 8080");
    private final JButton stop = new JButton("Dừng toàn bộ dịch vụ");
    private final DefaultTableModel rows = new DefaultTableModel(
            new String[]{"Mã phòng", "ID nội bộ", "Giáo viên tạo phòng", "Master version", "Giai đoạn / vòng", "Người giữ quyền", "Thành viên (session | tên)", "Hàng đợi"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable rooms = new JTable(rows);
    private final DefaultListModel<String> revisions = new DefaultListModel<>();
    private final JList<String> history = new JList<>(revisions);
    private final JTextArea code = new JTextArea();
    private final JTextArea activity = new JTextArea();
    private final JTextArea grading = new JTextArea();
    private final DefaultListModel<String> branchRows = new DefaultListModel<>();
    private final JList<String> branchList = new JList<>(branchRows);
    private final JTextArea branchCode = new JTextArea();
    private final JLabel classroomStatus = new JLabel("Chọn phòng để xem nhánh học sinh.");
    private java.util.Map<String, ServerMonitor.RoomView> monitorRooms = java.util.Map.of();
    private java.util.List<ServerMonitor.BranchView> displayedBranches = java.util.List.of();
    private String historyRoomId;
    private volatile String monitoredRoomId = "";
    private final ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "studyfix-server-manager-monitor");
        t.setDaemon(true);
        return t;
    });
    private volatile Server server;
    private volatile AssignmentTcpServer assignments;
    private volatile WebGatewayServer webGateway;
    private volatile AssignmentModuleConfig config;
    private volatile Server.Snapshot latest;
    private volatile boolean closing;
    private boolean rendering;

    private ServerAdmin() {
        code.setEditable(false);
        branchCode.setEditable(false);
        branchCode.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        activity.setEditable(false);
        grading.setEditable(false);
        code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        rooms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        branchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grading.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        activity.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel statusLines = new JPanel();
        statusLines.setLayout(new BoxLayout(statusLines, BoxLayout.Y_AXIS));
        statusLines.add(status);
        statusLines.add(Box.createVerticalStrut(5));
        statusLines.add(serviceStatus);
        JPanel top = new JPanel(new BorderLayout(12, 8));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        top.add(statusLines, BorderLayout.CENTER);
        top.add(stop, BorderLayout.EAST);

        JSplitPane revisionPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(history), new JScrollPane(code));
        revisionPane.setResizeWeight(.35);
        JTabbedPane tabs = new JTabbedPane();
        JPanel masterPanel = new JPanel(new BorderLayout());
        masterPanel.add(new JLabel("Master của phòng đã chọn — nhánh riêng chỉ thay Master sau khi giáo viên Merge."), BorderLayout.NORTH);
        masterPanel.add(revisionPane);
        tabs.addTab("Master và lịch sử", masterPanel);
        JSplitPane branchPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(branchList), new JScrollPane(branchCode));
        branchPane.setResizeWeight(.3);
        JPanel classroomPanel = new JPanel(new BorderLayout());
        classroomPanel.add(classroomStatus, BorderLayout.NORTH);
        classroomPanel.add(branchPane);
        tabs.addTab("Nhánh học sinh và kiểm tra", classroomPanel);
        tabs.addTab("Bài tập & chấm", new JScrollPane(grading));
        tabs.addTab("Nhật ký hoạt động (500 dòng gần nhất)", new JScrollPane(activity));
        JSplitPane body = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(rooms), tabs);
        body.setResizeWeight(.4);
        frame.add(top, BorderLayout.NORTH);
        frame.add(body);
        frame.setSize(1180, 740);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(true); }
        });
        stop.addActionListener(e -> shutdown(false));
        rooms.getSelectionModel().addListSelectionListener(e -> {
            if (!rendering && !e.getValueIsAdjusting()) showHistory();
        });
        history.addListSelectionListener(e -> {
            if (!rendering && !e.getValueIsAdjusting()) showRevision();
        });
        branchList.addListSelectionListener(e -> {
            if (!rendering && !e.getValueIsAdjusting()) showBranch();
        });
        frame.setVisible(true);

        background.execute(this::startServices);
    }

    private void startServices() {
        Server newCore = null;
        AssignmentTcpServer newAssignments = null;
        WebGatewayServer newWebGateway = null;
        try {
            config = AssignmentModuleConfig.load();
            // Nếu test hoặc công cụ chỉ đổi đường dẫn database chính thì đặt database học tập cạnh đó,
            // trừ khi đã chỉ định đường dẫn riêng. Script chạy chính thức truyền cả hai đường dẫn.
            if (System.getProperty("studyfix.learning.db") == null
                    && System.getProperty("studyfix.db") != null) {
                java.nio.file.Path corePath = config.coreDatabase();
                java.nio.file.Path learningPath = corePath.resolveSibling("studyfix-learning.db");
                config = new AssignmentModuleConfig(config.controlPort(), config.screenPort(),
                        config.assignmentPort(), corePath, learningPath, config.queueSize(),
                        config.workers(), config.dockerImage());
            }
            // Server đọc cấu hình relay màn hình và database từ thuộc tính hệ thống.
            System.setProperty("studyfix.port", Integer.toString(config.controlPort()));
            System.setProperty("studyfix.screen.port", Integer.toString(config.screenPort()));
            System.setProperty("studyfix.assignment.port", Integer.toString(config.assignmentPort()));
            System.setProperty("studyfix.db", config.coreDatabase().toString());
            System.setProperty("studyfix.learning.db", config.learningDatabase().toString());

            newCore = new Server(config.controlPort(), config.coreDatabase());
            newAssignments = new AssignmentTcpServer(newCore.gateway(), config.learningDatabase(),
                    config.assignmentPort(), config.queueSize(), config.workers(), config.dockerImage());
            newWebGateway = new WebGatewayServer(newCore, newAssignments);
            if (closing) throw new IllegalStateException("Đã yêu cầu dừng trong lúc khởi động.");
            newCore.start();
            newAssignments.start();
            newWebGateway.start();
            server = newCore;
            assignments = newAssignments;
            webGateway = newWebGateway;
            Server coreForHook = newCore;
            AssignmentTcpServer assignmentForHook = newAssignments;
            WebGatewayServer webForHook = newWebGateway;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                webForHook.close();
                assignmentForHook.close();
                coreForHook.close();
            }, "studyfix-shutdown"));

            final Server core = newCore;
            final AssignmentTcpServer assignmentServer = newAssignments;
            background.scheduleWithFixedDelay(() -> {
                try {
                    java.util.Map<String, ServerMonitor.RoomView> details = ServerMonitor.capture(core, monitoredRoomId);
                    Server.Snapshot snapshot = core.snapshot();
                    AssignmentService.Metrics metrics = assignmentServer.metrics();
                    boolean assignmentRunning = assignmentServer.isRunning();
                    SwingUtilities.invokeLater(() -> {
                        if (closing) return;
                        monitorRooms = details;
                        render(snapshot, metrics, assignmentRunning);
                    });
                } catch (Exception failure) {
                    // A transient database read must not permanently cancel the scheduled monitor.
                    core.log("Không cập nhật được Server Manager: " + failure.getClass().getSimpleName());
                }
            }, 0, 1, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (newWebGateway != null) newWebGateway.close();
            if (newAssignments != null) newAssignments.close();
            if (newCore != null) newCore.close();
            server = null;
            assignments = null;
            webGateway = null;
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            SwingUtilities.invokeLater(() -> {
                status.setText("Không khởi động được server: " + message);
                serviceStatus.setText("Kiểm tra cổng 5000/5001/5002, JAVA_HOME và database.");
                grading.setText("Server chưa khởi động.\n\n" + message +
                        "\n\nĐóng server khác đang dùng các cổng 5000, 5001 hoặc 5002 rồi thử lại.");
                stop.setText("Đóng cửa sổ");
                stop.setEnabled(true);
                for (ActionListener listener : stop.getActionListeners()) stop.removeActionListener(listener);
                stop.addActionListener(event -> shutdown(true));
            });
            background.shutdown();
        }
    }

    private String selectedRoomId() {
        int index = rooms.getSelectedRow();
        return index < 0 ? null : (String) rows.getValueAt(index, 1);
    }

    private Room.Snapshot selectedRoom() {
        String id = selectedRoomId();
        return latest == null ? null : latest.rooms().stream()
                .filter(r -> r.roomId().equals(id)).findFirst().orElse(null);
    }

    private void render(Server.Snapshot snapshot) {
        if (closing) return;
        AssignmentTcpServer assignmentServer = assignments;
        AssignmentService.Metrics metrics = assignmentServer != null
                ? assignmentServer.metrics()
                : new AssignmentService.Metrics(0, 0, 0, 0, "eclipse-temurin:21-jdk", false);
        render(snapshot, metrics, assignmentServer != null && assignmentServer.isRunning());
    }

    private void render(Server.Snapshot snapshot, AssignmentService.Metrics metrics, boolean assignmentRunning) {
        if (closing) return;
        renderSnapshot(snapshot);
        boolean running = snapshot.running();
        status.setText((running ? "ĐANG CHẠY" : "ĐÃ DỪNG") + " | Kết nối: " + snapshot.connections()
                + " | Số phòng: " + snapshot.rooms().size());
        serviceStatus.setText("Control TCP " + snapshot.port() + (running ? " ✓" : " ✕")
                + "    ·    Screen TCP " + (server == null ? config.screenPort() : server.screenSharePort()) + (running ? " ✓" : " ✕")
                + "    ·    Assignment TCP " + config.assignmentPort() + (assignmentRunning ? " ✓" : " ✕")
                + "    ·    Web " + (webGateway == null ? "HTTP " : webGateway.scheme().toUpperCase(java.util.Locale.ROOT) + " ") + (webGateway == null ? Integer.getInteger("studyfix.web.port", 8080) : webGateway.port())
                + (webGateway != null && webGateway.isRunning() ? " ✓" : " ✕"));
        grading.setText("STUDYFIX ASSIGNMENT / GRADING\n"
                + "============================\n"
                + "Listener cổng: " + config.assignmentPort() + " — " + (assignmentRunning ? "ĐANG CHẠY" : "ĐÃ DỪNG") + "\n"
                + "Trạng thái module: " + (metrics.running() ? "Hoạt động" : "Đã đóng") + "\n"
                + "Worker cấu hình: " + metrics.workerCount() + "\n"
                + "Đang chấm: " + metrics.activeTasks() + "\n"
                + "Đang chờ: " + metrics.queuedTasks() + "/" + metrics.queueCapacity() + "\n"
                + "Đã chấm, đang thử lưu lại database: " + (assignments == null ? 0 : assignments.pendingResultWrites()) + "\n"
                + "Docker image: " + metrics.dockerImage() + "\n\n"
                + "Lưu ý: listener đang chạy không bảo đảm Docker Engine/image đã sẵn sàng.\n"
                + "Kết quả chấm thật được hiển thị trong Client; nếu Docker chưa sẵn sàng,\n"
                + "StudyFix phải trả SANDBOX_UNAVAILABLE thay vì báo PASS/FAILED giả.\n");
    }

    /** Cũng gọi một lần sau khi tắt server, khi đã lấy xong trạng thái cuối cùng ngoài luồng giao diện. */
    private void renderSnapshot(Server.Snapshot snapshot) {
        rendering = true;
        String selected = selectedRoomId();
        latest = snapshot;
        rows.setRowCount(0);
        for (Room.Snapshot room : snapshot.rooms()) {
            ServerMonitor.RoomView detail = monitorRooms.get(room.roomId());
            rows.addRow(new Object[]{detail == null ? "Đang đồng bộ…" : detail.code(), room.roomId(),
                    detail == null ? room.ownerId() : detail.owner(), room.version(),
                    detail == null ? "" : detail.phase() + " / " + detail.round(), room.editor(), room.members(), room.queue()});
            if (room.roomId().equals(selected)) rooms.setRowSelectionInterval(rows.getRowCount() - 1, rows.getRowCount() - 1);
        }
        if (rooms.getSelectedRow() < 0 && rows.getRowCount() > 0) rooms.setRowSelectionInterval(0, 0);
        activity.setText(String.join("\n", snapshot.log()));
        rendering = false;
        showHistory();
    }

    private void showHistory() {
        int selected = history.getSelectedIndex();
        int previousSize = revisions.size();
        rendering = true;
        revisions.clear();
        Room.Snapshot room = selectedRoom();
        monitoredRoomId = room == null ? "" : room.roomId();
        if (room == null) {
            code.setText("");
            historyRoomId = null;
            rendering = false;
            showBranches();
            return;
        }
        for (CodeRevision revision : room.history()) revisions.addElement(revision.summary());
        int next = ServerMonitor.historySelection(historyRoomId, room.roomId(), selected, previousSize, revisions.size());
        if (next >= 0) history.setSelectedIndex(next);
        historyRoomId = room.roomId();
        rendering = false;
        showRevision();
        showBranches();
    }

    private void showRevision() {
        Room.Snapshot room = selectedRoom();
        int index = history.getSelectedIndex();
        if (room != null && index >= 0 && index < room.history().size()) {
            String text = room.history().get(index).detail();
            if (!code.getText().equals(text)) { code.setText(text); code.setCaretPosition(0); }
        }
    }

    private void showBranches() {
        int selected = branchList.getSelectedIndex();
        String key = selected >= 0 && selected < displayedBranches.size() ? displayedBranches.get(selected).key() : null;
        String previousRoom = (String) branchList.getClientProperty("roomId");
        String roomId = selectedRoomId();
        ServerMonitor.RoomView detail = monitorRooms.get(roomId == null ? "" : roomId);
        rendering = true;
        branchRows.clear();
        displayedBranches = detail == null ? java.util.List.of() : detail.branches();
        int next = -1;
        for (ServerMonitor.BranchView branch : displayedBranches) {
            branchRows.addElement(branch.summary());
            if (java.util.Objects.equals(previousRoom, roomId) && branch.key().equals(key)) next = branchRows.size() - 1;
        }
        branchList.putClientProperty("roomId", roomId);
        if (!branchRows.isEmpty()) branchList.setSelectedIndex(next < 0 ? 0 : next);
        classroomStatus.setText(detail == null ? "Chọn phòng để xem nhánh học sinh."
                : !detail.detailsLoaded() ? "Đang tải nhánh của phòng " + detail.code() + "…"
                : "Phòng " + detail.code() + " · " + detail.phase() + " · Vòng " + detail.round() + " · " + detail.branches().size() + " nhánh (chỉ xem)");
        rendering = false;
        showBranch();
    }

    private void showBranch() {
        int selected = branchList.getSelectedIndex();
        String text = selected >= 0 && selected < displayedBranches.size() ? displayedBranches.get(selected).detail()
                : "Chưa có nhánh trong vòng hiện tại. Giáo viên cần tách nhánh cho học sinh.\nMaster và lịch sử được xem ở tab riêng.";
        if (!branchCode.getText().equals(text)) { branchCode.setText(text); branchCode.setCaretPosition(0); }
    }

    private void shutdown(boolean dispose) {
        if (closing) {
            if (dispose) frame.dispose();
            return;
        }
        closing = true;
        stop.setEnabled(false);
        status.setText("Đang dừng toàn bộ dịch vụ…");
        if (background.isShutdown()) {
            if (dispose) frame.dispose();
            return;
        }
        background.execute(() -> {
            WebGatewayServer web = webGateway;
            AssignmentTcpServer assignmentServer = assignments;
            Server core = server;
            if (web != null) web.close();
            if (assignmentServer != null) assignmentServer.close();
            if (core != null) core.close();
            Server.Snapshot stopped = core == null ? null : core.snapshot();
            background.shutdownNow();
            SwingUtilities.invokeLater(() -> {
                if (stopped != null) {
                    renderSnapshot(stopped);
                    status.setText("ĐÃ DỪNG | Kết nối: " + stopped.connections() + " | Số phòng: " + stopped.rooms().size());
                    serviceStatus.setText("Control 5000 ✕    ·    Screen 5001 ✕    ·    Assignment 5002 ✕    ·    Web 8080 ✕");
                    grading.setText("Các dịch vụ đã dừng. Chạy lại run-server-manager.ps1 để khởi động lại.\n");
                } else {
                    status.setText("Server đã dừng.");
                }
                stop.setEnabled(false);
                stop.setText("Đã dừng");
                if (dispose) frame.dispose();
            });
        });
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(ServerAdmin::new); }
}
