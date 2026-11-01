package vn.studyfix.client.ui;

import vn.studyfix.client.config.ClientConfig;
import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.integration.ClientModuleContextAdapter;
import vn.studyfix.client.exercise.ExerciseModule;
import vn.studyfix.client.model.SessionInfo;
import vn.studyfix.client.net.ConnectionListener;
import vn.studyfix.client.net.TcpControlConnection;
import vn.studyfix.client.protocol.ServerMessageParser;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class MainFrame extends JFrame {
    private final TcpControlConnection connection;
    private final RoomController controller;
    private final JLabel connectionStatus = new JLabel("Đang kết nối");
    private final JLabel identity = new JLabel();
    private final RoomControlsPanel roomControls;
    private final SharedCodePanel codePanel;
    private final MembersPanel membersPanel;
    private final ChatPanel chatPanel;
    private final HistoryPanel historyPanel;
    private final ScreenSharePanel screenSharePanel;
    private final ExerciseModule exerciseModule;

    public MainFrame(ClientConfig config, TcpControlConnection connection, SessionInfo session) {
        super("StudyFix · Phòng thực hành lập trình");
        this.connection = connection; this.controller = new RoomController(connection, session);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(1050, 720)); setSize(1280, 850); setLocationRelativeTo(null);
        identity.setText(session.displayName() + " (" + session.username() + ") · " + session.role());
        roomControls = new RoomControlsPanel(controller); codePanel = new SharedCodePanel(controller); membersPanel = new MembersPanel(controller);
        chatPanel = new ChatPanel(controller); historyPanel = new HistoryPanel(controller); screenSharePanel = new ScreenSharePanel(controller, config); exerciseModule = new ExerciseModule(new ClientModuleContextAdapter(controller), connection.remoteHost(), Integer.getInteger("studyfix.assignment.port", 5002));
        buildUi(); registerConnection();
        addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); } });
    }
    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(8, 5)); header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel title = new JLabel("StudyFix"); title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel account = new JPanel(new FlowLayout(FlowLayout.TRAILING)); account.add(identity); account.add(new JSeparator(SwingConstants.VERTICAL)); account.add(connectionStatus);
        header.add(title, BorderLayout.WEST); header.add(account, BorderLayout.EAST);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Tổng quan", buildOverview());
        tabs.addTab("Phòng học", buildRoomWorkspace());
        tabs.addTab("Bài tập", exerciseModule.view());
        tabs.addTab("Thi & xếp hạng", exerciseModule.contestView());
        add(header, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout()); center.add(roomControls, BorderLayout.NORTH); center.add(tabs, BorderLayout.CENTER); add(center, BorderLayout.CENTER);
        controller.addStateObserver(() -> roomControls.refresh());
    }
    private JComponent buildOverview() {
        JPanel overview = new JPanel(new BorderLayout(10, 10));
        JLabel welcome = new JLabel("Tổng quan StudyFix · phòng học, bài tập và kỳ thi");
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 17f));
        welcome.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
        JPanel summary = new JPanel(new GridLayout(1, 3, 8, 8));
        summary.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        summary.add(infoCard("Phòng học", "Code cộng tác · Chat · Thành viên"));
        summary.add(infoCard("Bài tập", "Đề được giao · Nộp bài · Kết quả"));
        summary.add(infoCard("Thi & xếp hạng", "Kỳ thi · Test case · Bảng xếp hạng"));
        JPanel north = new JPanel(new BorderLayout()); north.add(welcome, BorderLayout.NORTH); north.add(summary, BorderLayout.CENTER);
        overview.add(north, BorderLayout.NORTH);
        overview.add(new ActivityPanel(controller), BorderLayout.CENTER);
        return overview;
    }

    private JPanel infoCard(String heading, String description) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(210, 220, 234)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JLabel title = new JLabel(heading); title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        card.add(title, BorderLayout.NORTH); card.add(new JLabel("<html>" + description.replace(" · ", "<br>") + "</html>"), BorderLayout.CENTER);
        return card;
    }

    private JComponent buildRoomWorkspace() {
        JTabbedPane codeAndShare = new JTabbedPane();
        JSplitPane collaboration = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codePanel, exerciseModule.collaborationToolsView());
        collaboration.setResizeWeight(0.69); collaboration.setOneTouchExpandable(true); collaboration.setDividerLocation(0.69);
        codeAndShare.addTab("Code cộng tác", collaboration);
        codeAndShare.addTab("Chia sẻ màn hình", screenSharePanel);

        JTabbedPane sidebar = new JTabbedPane();
        sidebar.addTab("Chat", chatPanel);
        sidebar.addTab("Thành viên", membersPanel);
        if (controller.session().isTeacher()) sidebar.addTab("Quyền sửa", buildEditRightsPanel());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, codeAndShare, sidebar);
        mainSplit.setResizeWeight(0.76); mainSplit.setOneTouchExpandable(true);
        mainSplit.setDividerLocation(820);
        JSplitPane full = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplit, historyPanel);
        full.setResizeWeight(0.84); full.setOneTouchExpandable(true); full.setDividerLocation(620);
        return full;
    }

    private JComponent buildEditRightsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JLabel editor = new JLabel("Người giữ quyền: chưa có");
        JTextArea queue = new JTextArea(6, 20); queue.setEditable(false); queue.setLineWrap(true); queue.setWrapStyleWord(true);
        JButton requestEdit = new JButton("Xin quyền sửa"); JButton releaseEdit = new JButton("Trả quyền");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING)); actions.add(requestEdit); actions.add(releaseEdit);
        panel.add(editor, BorderLayout.NORTH); panel.add(new JScrollPane(queue), BorderLayout.CENTER); panel.add(actions, BorderLayout.SOUTH);
        Runnable refresh = () -> {
            var state = controller.state();
            editor.setText("Người giữ quyền: " + (state.editorName() == null || state.editorName().isBlank() ? "chưa có" : state.editorName()));
            queue.setText("Hàng đợi: " + state.queue());
            boolean inRoom = state.inRoom(); requestEdit.setEnabled(inRoom);
            releaseEdit.setEnabled(inRoom && state.token() >= 0 && controller.session().displayName().equals(state.editorName()));
        };
        requestEdit.addActionListener(e -> controller.sendSafely(controller.code()::requestEditPriority));
        releaseEdit.addActionListener(e -> controller.sendSafely(() -> {
            long token = controller.state().token(); controller.code().release(token);
        }));
        controller.addStateObserver(refresh); refresh.run();
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    private void registerConnection() {
        connection.addListener(new ConnectionListener() {
            @Override public void onMessage(String message) {
                var parsed = new ServerMessageParser().parse(message);
                SwingUtilities.invokeLater(() -> controller.accept(parsed));
            }
            @Override public void onDisconnected(Throwable cause) {
                SwingUtilities.invokeLater(() -> { connectionStatus.setText("Mất kết nối"); connectionStatus.setForeground(Color.RED); });
            }
        });
        connectionStatus.setText("Đã đăng nhập · TCP " + connection.remoteHost() + ":" + connection.remotePort());
        connectionStatus.setForeground(new Color(0, 120, 40));
    }
    private void shutdown() {
        int answer = JOptionPane.showConfirmDialog(this, "Thoát StudyFix và đóng kết nối server?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        connection.close(); dispose();
    }
}
