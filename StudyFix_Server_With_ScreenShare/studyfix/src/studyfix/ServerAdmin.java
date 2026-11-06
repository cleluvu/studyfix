package studyfix;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Server administration only. All I/O and snapshot collection run off the EDT. */
public final class ServerAdmin {
    private final JFrame frame = new JFrame("StudyFix — Quản trị server");
    private final JLabel status = new JLabel("Đang khởi động server…");
    private final JButton stop = new JButton("Dừng server");
    private final DefaultTableModel rows = new DefaultTableModel(
            new String[]{"Phòng", "Owner userId", "Version", "Người giữ quyền", "Thành viên (session | tên)", "Hàng đợi"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable rooms = new JTable(rows);
    private final DefaultListModel<String> revisions = new DefaultListModel<>();
    private final JList<String> history = new JList<>(revisions);
    private final JTextArea code = new JTextArea();
    private final JTextArea activity = new JTextArea();
    private final ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor();
    private Server server;
    private Server.Snapshot latest;
    private volatile boolean closing;
    private boolean rendering;
    private ServerAdmin() {
        code.setEditable(false); activity.setEditable(false);
        code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        rooms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel top = new JPanel(new BorderLayout(12, 8));
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        top.add(status, BorderLayout.CENTER); top.add(stop, BorderLayout.EAST);
        JSplitPane revisionPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(history), new JScrollPane(code));
        revisionPane.setResizeWeight(.35);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Code và lịch sử (chỉ xem)", revisionPane);
        tabs.addTab("Nhật ký hoạt động (500 dòng gần nhất)", new JScrollPane(activity));
        JSplitPane body = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(rooms), tabs);
        body.setResizeWeight(.4);
        frame.add(top, BorderLayout.NORTH); frame.add(body);
        frame.setSize(1150, 700); frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { shutdown(true); } });
        stop.addActionListener(e -> shutdown(false));
        rooms.getSelectionModel().addListSelectionListener(e -> { if (!rendering && !e.getValueIsAdjusting()) showHistory(); });
        history.addListSelectionListener(e -> { if (!rendering && !e.getValueIsAdjusting()) showRevision(); });
        frame.setVisible(true);
        background.execute(() -> {
            try {
                server = new Server(Integer.getInteger("studyfix.port", 5000),
                        Path.of(System.getProperty("studyfix.db", "data/studyfix.db")));
                Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                server.start();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { status.setText("Không khởi động được: " + e.getMessage()); stop.setEnabled(false); });
                background.shutdown(); return;
            }
            background.scheduleWithFixedDelay(() -> {
                Server.Snapshot snapshot = server.snapshot();
                SwingUtilities.invokeLater(() -> render(snapshot));
            }, 0, 1, TimeUnit.SECONDS);
        });
    }
    private String selectedRoomId() {
        int index = rooms.getSelectedRow();
        return index < 0 ? null : (String) rows.getValueAt(index, 0);
    }
    private Room.Snapshot selectedRoom() {
        String id = selectedRoomId();
        return latest == null ? null : latest.rooms().stream().filter(r -> r.roomId().equals(id)).findFirst().orElse(null);
    }
    private void render(Server.Snapshot snapshot) {
        if (closing) return;
        renderSnapshot(snapshot);
    }
    /** Also used once after shutdown, after the final snapshot is collected off EDT. */
    private void renderSnapshot(Server.Snapshot snapshot) {
        rendering = true;
        String selected = selectedRoomId(); latest = snapshot;
        status.setText((snapshot.running() ? "Đang chạy" : "Đã dừng") + " | TCP " + snapshot.port() + " | Kết nối: " + snapshot.connections());
        rows.setRowCount(0);
        for (Room.Snapshot room : snapshot.rooms()) {
            rows.addRow(new Object[]{room.roomId(), room.ownerId(), room.version(), room.editor(), room.members(), room.queue()});
            if (room.roomId().equals(selected)) rooms.setRowSelectionInterval(rows.getRowCount()-1, rows.getRowCount()-1);
        }
        if (rooms.getSelectedRow() < 0 && rows.getRowCount() > 0) rooms.setRowSelectionInterval(0, 0);
        activity.setText(String.join("\n", snapshot.log()));
        rendering = false; showHistory();
    }
    private void showHistory() {
        int selected = history.getSelectedIndex();
        rendering = true;
        revisions.clear();
        Room.Snapshot room = selectedRoom();
        if (room == null) { code.setText(""); rendering = false; return; }
        for (CodeRevision revision : room.history()) revisions.addElement(revision.summary());
        if (!revisions.isEmpty()) history.setSelectedIndex(selected >= 0 && selected < revisions.size() ? selected : revisions.size()-1);
        rendering = false; showRevision();
    }
    private void showRevision() {
        Room.Snapshot room = selectedRoom(); int index = history.getSelectedIndex();
        if (room != null && index >= 0 && index < room.history().size()) {
            code.setText(room.history().get(index).detail()); code.setCaretPosition(0);
        }
    }
    private void shutdown(boolean dispose) {
        if (closing) { if (dispose) frame.dispose(); return; }
        closing = true; stop.setEnabled(false); status.setText("Đang dừng và dọn tài nguyên…");
        if (background.isShutdown()) { if (dispose) frame.dispose(); return; }
        background.execute(() -> {
            if (server != null) server.close();
            Server.Snapshot stopped = server == null ? null : server.snapshot();
            background.shutdownNow();
            SwingUtilities.invokeLater(() -> {
                if (stopped != null) renderSnapshot(stopped);
                else status.setText("Server đã dừng.");
                if (dispose) frame.dispose();
            });
        });
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(ServerAdmin::new); }
}
