package vn.studyfix.client.ui;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.protocol.ServerMessage;

import javax.swing.*;
import java.awt.*;

final class HistoryPanel extends JPanel {
    private final RoomController controller;
    private final JTextArea history = new JTextArea();
    private final JTextField version = new JTextField(7);
    private final JButton all = UiStyle.button("Danh sách lịch sử");
    private final JButton one = UiStyle.button("Xem version");
    HistoryPanel(RoomController controller) {
        super(new BorderLayout(8, 8)); this.controller = controller;
        history.setEditable(false); history.setFont(UiStyle.MONO);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
        top.add(all); top.add(new JLabel("Version:")); top.add(version); top.add(one);
        add(top, BorderLayout.NORTH); add(new JScrollPane(history), BorderLayout.CENTER); UiStyle.pad(this);
        all.addActionListener(e -> controller.sendSafely(controller.code()::history));
        one.addActionListener(e -> {
            try { controller.code().history(Long.parseLong(version.getText().trim())); }
            catch (Exception ex) { controller.notifyLocalError("Nhập version là số nguyên không âm."); }
        });
        controller.addMessageObserver(this::onMessage); controller.addStateObserver(this::refresh); refresh();
    }
    private void refresh() { all.setEnabled(controller.state().inRoom()); one.setEnabled(controller.state().inRoom()); version.setEnabled(controller.state().inRoom()); }
    private void onMessage(ServerMessage msg) {
        if (msg.type() == ServerMessage.Type.HISTORY || msg.raw().startsWith("[Xem lịch sử phòng")) {
            history.setText(msg.raw()); history.setCaretPosition(0);
        }
    }
}
