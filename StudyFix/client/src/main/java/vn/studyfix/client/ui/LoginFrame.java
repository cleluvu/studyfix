package vn.studyfix.client.ui;

import vn.studyfix.client.config.ClientConfig;
import vn.studyfix.client.service.AuthService;

import javax.swing.*;
import java.awt.*;

public final class LoginFrame extends JFrame {
    private final ClientConfig config;
    private final AuthService auth;
    private final JTextField host = new JTextField();
    private final JTextField port = new JTextField();
    private final JTextField username = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JButton login = UiStyle.button("Kết nối và đăng nhập");
    private final JLabel status = new JLabel("Nhập địa chỉ server StudyFix trong mạng LAN.");

    public LoginFrame(ClientConfig config, AuthService auth) {
        super("StudyFix · Đăng nhập"); this.config = config; this.auth = auth;
        host.setText(config.defaultHost()); port.setText(Integer.toString(config.controlPort()));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(460, 350)); setSize(510, 390); setLocationRelativeTo(null);
        buildUi();
    }
    private void buildUi() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 26, 12, 26));
        GridBagConstraints g = new GridBagConstraints(); g.insets = new Insets(7, 6, 7, 6); g.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, g, 0, "IP / Host server", host); addRow(form, g, 1, "Cổng TCP", port);
        addRow(form, g, 2, "Tài khoản", username); addRow(form, g, 3, "Mật khẩu", password);
        JLabel title = new JLabel("StudyFix", SwingConstants.CENTER); title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        JLabel subtitle = new JLabel("Phòng thực hành lập trình qua mạng LAN", SwingConstants.CENTER);
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4)); header.add(title); header.add(subtitle);
        login.addActionListener(e -> doLogin());
        getRootPane().setDefaultButton(login);
        JPanel bottom = new JPanel(new BorderLayout(8, 8)); bottom.setBorder(BorderFactory.createEmptyBorder(0, 26, 20, 26));
        bottom.add(login, BorderLayout.NORTH); bottom.add(status, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH); add(form, BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);
    }
    private static void addRow(JPanel p, GridBagConstraints g, int row, String label, JComponent input) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; p.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1; p.add(input, g);
    }
    private void doLogin() {
        final int selectedPort;
        try { selectedPort = Integer.parseInt(port.getText().trim()); if (selectedPort < 1 || selectedPort > 65535) throw new NumberFormatException(); }
        catch (NumberFormatException e) { status.setText("Cổng phải nằm trong khoảng 1–65535."); return; }
        String h = host.getText().trim(), u = username.getText().trim(); char[] pw = password.getPassword();
        if (h.isEmpty() || u.isEmpty() || pw.length == 0) { status.setText("Hãy nhập host, tài khoản và mật khẩu."); return; }
        login.setEnabled(false); status.setText("Đang kết nối tới " + h + ":" + selectedPort + "...");
        new SwingWorker<AuthService.LoginResult, Void>() {
            @Override protected AuthService.LoginResult doInBackground() throws Exception {
                return auth.login(h, selectedPort, u, new String(pw));
            }
            @Override protected void done() {
                login.setEnabled(true); java.util.Arrays.fill(pw, '\0');
                try {
                    AuthService.LoginResult result = get();
                    MainFrame main = new MainFrame(config, result.connection(), result.session());
                    main.setVisible(true); dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText(cause.getMessage() == null ? "Không thể đăng nhập." : cause.getMessage());
                }
            }
        }.execute();
    }
}
