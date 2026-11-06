package vn.studyfix.client.ui;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.protocol.ServerMessage;

import javax.swing.*;
import java.awt.*;

final class RoomControlsPanel extends JPanel {
    private final RoomController controller;
    private final JTextField roomId = new JTextField(9);
    private final JButton create = UiStyle.button("Tạo phòng");
    private final JButton join = UiStyle.button("Vào phòng");
    private final JButton leave = UiStyle.button("Rời phòng");
    private final JLabel state = new JLabel("Chưa vào phòng");

    RoomControlsPanel(RoomController controller) {
        super(new FlowLayout(FlowLayout.LEADING, 7, 5)); this.controller = controller;
        add(new JLabel("Phòng:")); add(roomId); add(create); add(join); add(leave); add(state);
        create.setVisible(controller.session().isTeacher());
        create.addActionListener(e -> controller.sendSafely(controller.rooms()::create));
        join.addActionListener(e -> controller.sendSafely(() -> controller.rooms().join(roomId.getText())));
        leave.addActionListener(e -> controller.sendSafely(controller.rooms()::leave));
        controller.addStateObserver(this::refresh);
        controller.addMessageObserver(this::onMessage);
        refresh();
    }
    private void onMessage(ServerMessage message) {
        if (message.raw().contains("Đã tạo và tham gia phòng:") || message.raw().contains("Đã tham gia phòng:")) {
            String[] parts = message.raw().trim().split("\\s+"); if (parts.length > 0) roomId.setText(controller.state().roomId() == null ? "" : controller.state().roomId());
        }
    }
    void refresh() {
        boolean inRoom = controller.state().inRoom();
        create.setEnabled(!inRoom && controller.session().isTeacher());
        join.setEnabled(!inRoom); roomId.setEnabled(!inRoom); leave.setEnabled(inRoom);
        state.setText(inRoom ? "Đang ở phòng " + controller.state().roomId() : "Chưa vào phòng");
    }
}
