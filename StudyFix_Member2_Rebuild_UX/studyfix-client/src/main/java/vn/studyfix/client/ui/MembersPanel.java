package vn.studyfix.client.ui;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.model.MemberInfo;
import vn.studyfix.client.protocol.ServerMessage;

import javax.swing.*;
import java.awt.*;

final class MembersPanel extends JPanel {
    private final RoomController controller;
    private final DefaultListModel<MemberInfo> model = new DefaultListModel<>();
    private final JList<MemberInfo> list = new JList<>(model);
    private final JButton refresh = UiStyle.button("Làm mới thành viên");
    private final JButton kick = UiStyle.button("Mời ra khỏi phòng");
    MembersPanel(RoomController controller) {
        super(new BorderLayout(8, 8)); this.controller = controller;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING)); controls.add(refresh);
        if (controller.session().isTeacher()) controls.add(kick);
        add(new JScrollPane(list), BorderLayout.CENTER); add(controls, BorderLayout.SOUTH); UiStyle.pad(this);
        refresh.addActionListener(e -> controller.sendSafely(controller.rooms()::requestMembers));
        kick.addActionListener(e -> doKick()); controller.addStateObserver(this::refreshModel);
        controller.addMessageObserver(this::onMessage); refreshModel();
    }
    private void doKick() {
        MemberInfo selected = list.getSelectedValue();
        if (selected == null) { JOptionPane.showMessageDialog(this, "Hãy chọn thành viên trước."); return; }
        if (selected.sessionId() == null || selected.sessionId().isBlank()) {
            JOptionPane.showMessageDialog(this, "Server chỉ gửi tên trong sự kiện cập nhật. Nhấn 'Làm mới thành viên' để lấy sessionId rồi thử lại."); return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Mời " + selected.displayName() + " ra khỏi phiên kết nối?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) controller.sendSafely(() -> controller.rooms().kick(selected.sessionId()));
    }
    private void onMessage(ServerMessage message) { if (message.type() == ServerMessage.Type.MEMBERS) refreshModel(); }
    private void refreshModel() {
        model.clear(); for (MemberInfo member : controller.state().members()) model.addElement(member);
        boolean active = controller.state().inRoom(); refresh.setEnabled(active); kick.setEnabled(active && controller.session().isTeacher());
    }
}
