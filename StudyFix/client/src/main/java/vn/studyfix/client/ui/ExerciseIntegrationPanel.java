package vn.studyfix.client.ui;

import vn.studyfix.client.integration.ClientModuleContext;
import vn.studyfix.client.integration.ClientModule;
import vn.studyfix.client.protocol.ServerMessage;

import javax.swing.*;
import java.awt.*;

/** Honest placeholder until member 3 and the server agree on an exercise/submission protocol. */
final class ExerciseIntegrationPanel extends JPanel implements ClientModule {
    private final JTextArea details = new JTextArea();
    ExerciseIntegrationPanel(ClientModuleContext context) {
        super(new BorderLayout(10, 10));
        JLabel title = new JLabel("Bài tập · Nộp bài · Chấm điểm · Thi"); title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setText("Module này được để sẵn vị trí để ghép phần của thành viên 3.\n\n"
                + "Source server hiện tại chưa có lệnh giao bài, nộp bài, worker chấm, thời hạn thi hoặc bảng xếp hạng.\n\n"
                + "Không giả lập kết quả thành công. Khi tích hợp, thành viên 3 triển khai ClientModule và sử dụng ClientModuleContext để gửi lệnh qua kết nối điều khiển dùng chung; server phải bổ sung hợp đồng lệnh/phản hồi tương ứng.\n\n"
                + "Tài liệu: docs/INTEGRATION_WITH_MEMBER3.md");
        add(title, BorderLayout.NORTH); add(new JScrollPane(details), BorderLayout.CENTER); UiStyle.pad(this);
        context.addMessageListener(this::onMessage);
    }
    @Override public String tabTitle() { return "Bài tập / Nộp bài"; }
    @Override public JComponent view() { return this; }
    private void onMessage(ServerMessage message) {
        if (message.raw().startsWith("[EXERCISE]") || message.raw().startsWith("[SUBMISSION]") || message.raw().startsWith("[CONTEST]")) {
            details.append("\n\nThông điệp server mới:\n" + message.raw());
        }
    }
}
