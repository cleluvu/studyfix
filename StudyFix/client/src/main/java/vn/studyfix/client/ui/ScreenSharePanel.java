package vn.studyfix.client.ui;

import vn.studyfix.client.config.ClientConfig;
import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.protocol.ServerMessage;
import vn.studyfix.client.screen.ScreenShareClient;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Meet-like screen share UI: the large stage is reserved for a remote live stream, not local diagnostics. */
final class ScreenSharePanel extends JPanel {
    private static final Pattern TICKET = Pattern.compile(
            "^\\[SCREEN_TICKET]\\s+mode=(PUBLISH|VIEW)\\s+room=(\\S+)\\s+port=(\\d+)\\s+ticket=(\\S+).*$");

    private final RoomController controller;
    @SuppressWarnings("unused")
    private final ClientConfig config;
    private final VideoStage stage = new VideoStage();
    private final JLabel status = new JLabel("Chưa có luồng màn hình trực tiếp.");
    private final JLabel quality = new JLabel("Chia sẻ màn hình qua mạng LAN");
    private final JButton start = UiStyle.button("Chia sẻ màn hình của tôi");
    private final JButton view = UiStyle.button("Xem màn hình trực tiếp");
    private final JButton stop = UiStyle.button("Dừng");

    private volatile ScreenShareClient active;
    private volatile ScreenShareClient.Mode activeMode;
    private volatile boolean receivedFirstFrame;

    ScreenSharePanel(RoomController controller, ClientConfig config) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        this.config = config;
        setBackground(new Color(242, 245, 249));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 7));
        toolbar.add(start);
        toolbar.add(view);
        toolbar.add(stop);

        JPanel footer = new JPanel(new BorderLayout(10, 2));
        footer.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
        footer.add(status, BorderLayout.CENTER);
        quality.setForeground(new Color(95, 105, 120));
        footer.add(quality, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
        add(stage, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        UiStyle.pad(this);

        start.addActionListener(e -> requestTicket("PUBLISH"));
        view.addActionListener(e -> requestTicket("VIEW"));
        stop.addActionListener(e -> stopShare());

        controller.addStateObserver(this::refresh);
        controller.addMessageObserver(this::onMessage);
        refresh();
    }

    private void refresh() {
        boolean inRoom = controller.state().inRoom();
        boolean idle = active == null;
        start.setEnabled(inRoom && idle);
        view.setEnabled(inRoom && idle);
        stop.setEnabled(!idle);
    }

    private void requestTicket(String mode) {
        if (!controller.state().inRoom()) {
            status.setText("Bạn cần vào phòng trước khi chia sẻ màn hình.");
            return;
        }
        receivedFirstFrame = false;
        if ("PUBLISH".equals(mode)) {
            stage.showMessage("Đang bắt đầu chia sẻ", "Đang xin quyền từ server; cửa sổ này chưa phải nơi xem luồng của chính mình.");
            status.setText("Đang xin quyền chia sẻ màn hình...");
        } else {
            stage.showMessage("Đang kết nối", "Đang yêu cầu xem màn hình đang được chia sẻ trong phòng.");
            status.setText("Đang yêu cầu luồng màn hình từ server...");
        }
        controller.sendSafely(() -> controller.connection().send(
                "/screen-ticket " + (mode.equals("PUBLISH") ? "publish" : "view")));
    }

    private void onMessage(ServerMessage message) {
        String raw = message.raw();
        Matcher m = TICKET.matcher(raw);
        if (m.matches()) {
            connectStream(m.group(1), m.group(2), Integer.parseInt(m.group(3)), m.group(4));
            return;
        }
        if (raw.startsWith("Lệnh:") && raw.contains("/screen-ticket")) {
            status.setText("Server chưa nhận lệnh /screen-ticket. Hãy xác nhận đang chạy server có relay màn hình.");
            stage.showMessage("Server chưa hỗ trợ chia sẻ màn hình", "Cần chạy bản server đã tích hợp ScreenShareRelay.");
        } else if (raw.startsWith("[SCREEN_SHARE] STARTED")) {
            // Subscribe automatically for either role. The publisher already has activeMode=PUBLISH,
            // so its own STARTED event cannot cause it to open a VIEW ticket for itself.
            if (activeMode == null) {
                status.setText("Có người trong phòng vừa bắt đầu chia sẻ. Đang tự kết nối màn hình...");
                requestTicket("VIEW");
            } else if (activeMode == ScreenShareClient.Mode.VIEW) {
                status.setText("Có luồng chia sẻ trong phòng. Đang nhận màn hình trực tiếp...");
            }
        } else if (raw.startsWith("[SCREEN_SHARE] STOPPED")) {
            if (activeMode == null || activeMode == ScreenShareClient.Mode.VIEW) {
                status.setText("Người chia sẻ đã dừng luồng màn hình.");
            }
        }
    }

    private void connectStream(String modeText, String room, int port, String ticket) {
        if (active != null) active.close();
        ScreenShareClient.Mode mode = modeText.equals("PUBLISH")
                ? ScreenShareClient.Mode.PUBLISH : ScreenShareClient.Mode.VIEW;
        activeMode = mode;
        status.setText("Đang kết nối tới relay của phòng " + room + " qua cổng " + port + "...");
        stage.showMessage(mode == ScreenShareClient.Mode.PUBLISH
                        ? "Đang chia sẻ màn hình của bạn"
                        : "Đang kết nối tới màn hình trực tiếp",
                mode == ScreenShareClient.Mode.PUBLISH
                        ? "Những người xem trong phòng sẽ nhận được màn hình chính của máy này."
                        : "Khi nhận được ảnh đầu tiên, màn hình trình chiếu sẽ hiển thị tại đây.");

        AtomicBoolean firstFrame = new AtomicBoolean();
        new SwingWorker<ScreenShareClient, Void>() {
            @Override
            protected ScreenShareClient doInBackground() throws Exception {
                return ScreenShareClient.connect(controller.connection().remoteHost(), port, ticket, mode,
                        new ScreenShareClient.Listener() {
                            @Override
                            public void onImage(BufferedImage frame) {
                                SwingUtilities.invokeLater(() -> {
                                    stage.setFrame(frame);
                                    if (firstFrame.compareAndSet(false, true)) {
                                        receivedFirstFrame = true;
                                        status.setText("Đang nhận màn hình trực tiếp · " + frame.getWidth() + " × " + frame.getHeight());
                                        quality.setText("LIVE");
                                    }
                                });
                            }

                            @Override
                            public void onStatus(String text) {
                                SwingUtilities.invokeLater(() -> {
                                    if (text != null && !text.isBlank() && !firstFrame.get()) status.setText(text);
                                });
                            }

                            @Override
                            public void onClosed(Throwable reason) {
                                SwingUtilities.invokeLater(() -> {
                                    active = null;
                                    activeMode = null;
                                    refresh();
                                    stage.showMessage("Luồng màn hình đã kết thúc", "Chọn 'Xem màn hình trực tiếp' hoặc bắt đầu chia sẻ lại.");
                                    quality.setText("Chia sẻ màn hình qua mạng LAN");
                                    status.setText(reason == null
                                            ? "Đã đóng kết nối chia sẻ màn hình."
                                            : "Kết nối màn hình bị đóng: " + reason.getMessage());
                                });
                            }
                        });
            }

            @Override
            protected void done() {
                try {
                    ScreenShareClient candidate = get();
                    if (candidate.isClosed()) {
                        active = null;
                        activeMode = null;
                        status.setText("Relay đã đóng hoặc từ chối phiên màn hình.");
                        stage.showMessage("Không kết nối được", "Kiểm tra ticket, quyền trong phòng và log server.");
                    } else {
                        active = candidate;
                        if (mode == ScreenShareClient.Mode.PUBLISH) {
                            status.setText("Đang chia sẻ màn hình của bạn. Người xem sẽ thấy desktop của máy này.");
                            stage.showMessage("Bạn đang chia sẻ màn hình", "Để tránh hiệu ứng màn hình lồng nhau, không hiển thị bản thu local tại đây.");
                            quality.setText("ĐANG CHIA SẺ");
                        } else {
                            status.setText("Đã kết nối relay; đang chờ ảnh từ người chia sẻ...");
                            stage.showMessage("Đang chờ màn hình", "Người chia sẻ cần giữ phiên đang hoạt động.");
                            quality.setText("ĐANG KẾT NỐI");
                        }
                    }
                    refresh();
                } catch (Exception ex) {
                    active = null;
                    activeMode = null;
                    refresh();
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText("Không kết nối được relay màn hình: " + cause.getMessage());
                    stage.showMessage("Không kết nối được", "Kiểm tra IP server, cổng relay và trạng thái ticket.");
                    quality.setText("Chia sẻ màn hình qua mạng LAN");
                }
            }
        }.execute();
    }

    private void stopShare() {
        ScreenShareClient current = active;
        active = null;
        activeMode = null;
        receivedFirstFrame = false;
        if (current != null) current.close();
        stage.showMessage("Đã dừng luồng màn hình", "Bạn có thể bắt đầu chia sẻ hoặc xem một luồng khác.");
        status.setText("Đã dừng chia sẻ/xem màn hình.");
        quality.setText("Chia sẻ màn hình qua mạng LAN");
        refresh();
    }

    /** Video stage that keeps the stream's aspect ratio and re-fits on every resize. */
    private static final class VideoStage extends JPanel {
        private BufferedImage frame;
        private String title = "Chưa có màn hình trực tiếp";
        private String subtitle = "Chọn 'Xem màn hình trực tiếp' sau khi một thành viên bắt đầu chia sẻ.";

        VideoStage() {
            setOpaque(true);
            setBackground(new Color(24, 28, 36));
            setMinimumSize(new Dimension(480, 300));
        }

        void setFrame(BufferedImage image) {
            this.frame = image;
            repaint();
        }

        void showMessage(String title, String subtitle) {
            this.frame = null;
            this.title = title;
            this.subtitle = subtitle;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                if (frame != null) {
                    int inset = 12;
                    double scale = Math.min((double) (getWidth() - inset * 2) / frame.getWidth(),
                            (double) (getHeight() - inset * 2) / frame.getHeight());
                    scale = Math.max(0.01, scale);
                    int drawW = Math.max(1, (int) Math.round(frame.getWidth() * scale));
                    int drawH = Math.max(1, (int) Math.round(frame.getHeight() * scale));
                    int x = (getWidth() - drawW) / 2;
                    int y = (getHeight() - drawH) / 2;
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(frame, x, y, drawW, drawH, null);
                } else {
                    int centerY = getHeight() / 2;
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(new Color(235, 239, 245));
                    g.setFont(getFont().deriveFont(Font.BOLD, 21f));
                    drawCentered(g, title, centerY - 9);
                    g.setColor(new Color(177, 187, 201));
                    g.setFont(getFont().deriveFont(Font.PLAIN, 13f));
                    drawCenteredWrapped(g, subtitle, centerY + 23, Math.max(300, getWidth() - 100));
                }
            } finally {
                g.dispose();
            }
        }

        private void drawCentered(Graphics2D g, String value, int baseline) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(value, Math.max(12, (getWidth() - fm.stringWidth(value)) / 2), baseline);
        }

        private void drawCenteredWrapped(Graphics2D g, String value, int baseline, int maxWidth) {
            FontMetrics fm = g.getFontMetrics();
            String[] words = value.split("\\s+");
            String line = "";
            int y = baseline;
            for (String word : words) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && fm.stringWidth(next) > maxWidth) {
                    g.drawString(line, Math.max(12, (getWidth() - fm.stringWidth(line)) / 2), y);
                    y += fm.getHeight();
                    line = word;
                } else line = next;
            }
            if (!line.isEmpty()) g.drawString(line, Math.max(12, (getWidth() - fm.stringWidth(line)) / 2), y);
        }
    }
}
