package vn.studyfix.client;

import vn.studyfix.client.config.ClientConfig;
import vn.studyfix.client.service.AuthService;
import vn.studyfix.client.ui.LoginFrame;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;

public final class StudyFixApplication {
    private StudyFixApplication() { }
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        ClientConfig config = ClientConfig.load();
        SwingUtilities.invokeLater(() -> new LoginFrame(config, new AuthService(config)).setVisible(true));
    }
}
