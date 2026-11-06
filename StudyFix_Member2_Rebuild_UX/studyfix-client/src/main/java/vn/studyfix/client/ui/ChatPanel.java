package vn.studyfix.client.ui;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.protocol.ServerMessage;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class ChatPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final RoomController controller;
    private final JTextArea messages = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton send = UiStyle.button("Gửi");
    ChatPanel(RoomController controller) {
        super(new BorderLayout(8, 8)); this.controller = controller;
        messages.setEditable(false); messages.setLineWrap(true); messages.setWrapStyleWord(true);
        JPanel bottom = new JPanel(new BorderLayout(7, 0)); bottom.add(input, BorderLayout.CENTER); bottom.add(send, BorderLayout.EAST);
        add(new JScrollPane(messages), BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH); UiStyle.pad(this);
        send.addActionListener(e -> sendMessage()); input.addActionListener(e -> sendMessage());
        controller.addMessageObserver(this::onMessage);
        controller.addStateObserver(() -> { send.setEnabled(controller.state().inRoom()); input.setEnabled(controller.state().inRoom()); });
        send.setEnabled(false); input.setEnabled(false);
        append("Hệ thống", "Chat chỉ truyền tới thành viên của phòng hiện tại. Đang chờ vào phòng.");
    }
    private void sendMessage() {
        String text = input.getText();
        if (text.trim().isEmpty()) return;
        controller.sendSafely(() -> controller.rooms().chat(text)); input.setText("");
    }
    private void onMessage(ServerMessage message) {
        switch (message.type()) {
            case CHAT -> append("Chat", message.raw());
            case SYSTEM -> append("Hệ thống", message.raw());
            case MEMBERS -> append("Thành viên", message.raw());
            case ROOM_STATE -> append("Quyền sửa", message.raw());
            case GENERIC -> {
                String raw = message.raw();
                if (raw.startsWith("[Client]") || raw.startsWith("[SAVE_FAILED]") || raw.startsWith("Từ chối")
                        || raw.startsWith("Cách dùng:") || raw.startsWith("Bạn ") || raw.startsWith("Chỉ ")
                        || raw.startsWith("Đã ") || raw.startsWith("Không ") || raw.startsWith("Sai ")) append("Server", raw);
            }
            default -> { }
        }
    }
    private void append(String source, String text) {
        String shown = text == null ? "" : text;
        if (shown.length() > 3000) shown = shown.substring(0, 3000) + "… [đã rút gọn ở tab chat]";
        messages.append("[" + LocalTime.now().format(TIME) + "] " + source + ": " + shown.replace("\n", "\n    ") + "\n");
        messages.setCaretPosition(messages.getDocument().getLength());
    }
}
