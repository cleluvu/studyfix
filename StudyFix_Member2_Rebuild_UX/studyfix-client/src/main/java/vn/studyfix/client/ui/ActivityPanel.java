package vn.studyfix.client.ui;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.protocol.ServerMessage;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class ActivityPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final JTextArea log = new JTextArea();
    ActivityPanel(RoomController controller) {
        super(new BorderLayout()); log.setEditable(false); log.setFont(UiStyle.MONO);
        add(new JScrollPane(log), BorderLayout.CENTER); UiStyle.pad(this);
        controller.addMessageObserver(this::onMessage);
    }
    private void onMessage(ServerMessage message) {
        if (message.type() == ServerMessage.Type.PONG) return;
        String raw = message.raw();
        String shortText;
        if (message.type() == ServerMessage.Type.SHARED_CODE)
            shortText = "Đã nhận code chung, version=" + (message.version() == null ? "?" : message.version()) + ", ký tự=" + (message.code() == null ? 0 : message.code().length());
        else if (raw.length() > 1800) shortText = raw.substring(0, 1800) + "… [rút gọn; xem tab chuyên biệt]";
        else shortText = raw;
        log.append("[" + LocalDateTime.now().format(TIME) + "] " + shortText.replace("\n", "\n    ") + "\n\n");
        log.setCaretPosition(log.getDocument().getLength());
    }
}
