package vn.studyfix.client.ui;

import vn.studyfix.client.config.ClientConfig;
import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.integration.ClientModule;
import vn.studyfix.client.integration.ClientModuleContextAdapter;
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
    private final ClientModule exerciseModule;

    public MainFrame(ClientConfig config, TcpControlConnection connection, SessionInfo session) {
        super("StudyFix · Phòng thực hành lập trình");
        this.connection = connection; this.controller = new RoomController(connection, session);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(1050, 720)); setSize(1280, 850); setLocationRelativeTo(null);
        identity.setText(session.displayName() + " (" + session.username() + ") · " + session.role());
        roomControls = new RoomControlsPanel(controller); codePanel = new SharedCodePanel(controller); membersPanel = new MembersPanel(controller);
        chatPanel = new ChatPanel(controller); historyPanel = new HistoryPanel(controller); screenSharePanel = new ScreenSharePanel(controller, config); exerciseModule = new ExerciseIntegrationPanel(new ClientModuleContextAdapter(controller));
        buildUi(); registerConnection();
        addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosing(java.awt.event.WindowEvent e) { shutdown(); } });
    }
    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(8, 5)); header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel title = new JLabel("StudyFix"); title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel account = new JPanel(new FlowLayout(FlowLayout.TRAILING)); account.add(identity); account.add(new JSeparator(SwingConstants.VERTICAL)); account.add(connectionStatus);
        header.add(title, BorderLayout.WEST); header.add(account, BorderLayout.EAST);
        JTabbedPane tabs = new JTabbedPane(); tabs.addTab("Code cộng tác", codePanel); tabs.addTab("Chat", chatPanel); tabs.addTab("Thành viên", membersPanel); tabs.addTab("Lịch sử code", historyPanel); tabs.addTab("Chia sẻ màn hình", screenSharePanel); tabs.addTab(exerciseModule.tabTitle(), exerciseModule.view()); tabs.addTab("Hoạt động / phản hồi", new ActivityPanel(controller));
        add(header, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout()); center.add(roomControls, BorderLayout.NORTH); center.add(tabs, BorderLayout.CENTER); add(center, BorderLayout.CENTER);
        controller.addStateObserver(() -> roomControls.refresh());
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
