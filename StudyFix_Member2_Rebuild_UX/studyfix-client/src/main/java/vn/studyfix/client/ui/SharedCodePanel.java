package vn.studyfix.client.ui;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.model.RoomState;
import vn.studyfix.client.protocol.ServerMessage;
import vn.studyfix.client.protocol.WireCommands;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.Duration;

/**
 * Simplified collaborative editor UI for the existing server-authoritative line/range workflow.
 * Users select a contiguous region in shared code, edit that region directly in the draft editor,
 * preview the server-composed draft, then finish or cancel. The shared code is never edited locally.
 */
final class SharedCodePanel extends JPanel {
    private static final String PREVIEW_MARKER = "--- XEM TRƯỚC CODE (CHƯA LƯU) ---";

    private final RoomController controller;
    private final JTextArea codeArea = UiStyle.monoArea();
    private final JTextArea draftArea = UiStyle.monoArea();
    private final JLabel stateLine = new JLabel("Chưa tải code");
    private final JLabel selectionHint = new JLabel(
            "Bôi đen một hoặc nhiều dòng liên tiếp trong code chung, rồi chọn 'Mở vùng chọn thành bản nháp'.");
    private final JLabel draftTitle = new JLabel("Chưa có lượt sửa. Xin quyền sửa trước khi mở vùng chọn.");
    private final JLabel status = new JLabel("Các thay đổi chỉ được lưu khi server xác nhận hoàn tất lượt sửa.");

    private final JButton refresh = UiStyle.button("Tải code");
    private final JButton request = UiStyle.button("Xin quyền sửa");
    private final JButton release = UiStyle.button("Trả quyền");
    private final JButton openDraft = UiStyle.button("Mở vùng chọn thành bản nháp");
    private final JButton fullUpdate = UiStyle.button("Nạp toàn bộ code (GV)");
    // Keep exactly these three actions in the draft editor, per the streamlined workflow.
    private final JButton preview = UiStyle.button("Xem bản nháp");
    private final JButton finish = UiStyle.button("Hoàn tất lượt sửa");
    private final JButton cancel = UiStyle.button("Hủy lượt sửa");

    private PendingSelection pendingSelection;
    private boolean draftActive;
    private boolean draftDirty;
    private boolean draftAppliedToServer;
    private boolean suppressDraftChanges;
    private boolean previewOperationInProgress;
    private long draftToken = -1;
    private String activeRange = "";

    SharedCodePanel(RoomController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        codeArea.setEditable(false);
        codeArea.setLineWrap(false);
        draftArea.setEditable(false);
        draftArea.setLineWrap(false);

        JPanel top = new JPanel(new BorderLayout(8, 5));
        top.add(stateLine, BorderLayout.NORTH);
        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 3));
        toolRow.add(refresh);
        toolRow.add(request);
        toolRow.add(release);
        toolRow.add(openDraft);
        toolRow.add(fullUpdate);
        JPanel instructions = new JPanel(new BorderLayout(4, 2));
        instructions.add(selectionHint, BorderLayout.NORTH);
        instructions.add(toolRow, BorderLayout.CENTER);
        top.add(instructions, BorderLayout.CENTER);

        JPanel draftPanel = new JPanel(new BorderLayout(6, 6));
        draftPanel.setBorder(BorderFactory.createTitledBorder("Bản nháp — sửa trực tiếp vùng được chọn"));
        JPanel draftHeader = new JPanel(new BorderLayout(6, 4));
        draftHeader.add(draftTitle, BorderLayout.NORTH);
        JPanel draftActions = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 3));
        draftActions.add(preview);
        draftActions.add(finish);
        draftActions.add(cancel);
        draftHeader.add(draftActions, BorderLayout.CENTER);
        draftPanel.add(draftHeader, BorderLayout.NORTH);
        draftPanel.add(new JScrollPane(draftArea), BorderLayout.CENTER);
        draftPanel.setPreferredSize(new Dimension(500, 220));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(codeArea), draftPanel);
        split.setResizeWeight(0.65);
        split.setOneTouchExpandable(true);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        UiStyle.pad(this);

        refresh.addActionListener(e -> controller.sendSafely(controller.code()::refreshCode));
        request.addActionListener(e -> controller.sendSafely(controller.code()::requestEdit));
        release.addActionListener(e -> releaseEdit());
        openDraft.addActionListener(e -> openDraftFromSelection());
        preview.addActionListener(e -> previewDraft());
        finish.addActionListener(e -> finishDraft());
        cancel.addActionListener(e -> cancelDraft());
        fullUpdate.addActionListener(e -> wholeCodeUpdate());

        draftArea.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (suppressDraftChanges || !draftActive) return;
                draftDirty = true;
                draftAppliedToServer = false;
                status.setText("Bản nháp vừa thay đổi trên máy này; bấm 'Xem bản nháp' để gửi lên server trước khi hoàn tất.");
                refreshButtons();
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });

        controller.addStateObserver(this::refreshFromState);
        controller.addMessageObserver(this::onMessage);
        refreshFromState();
        refreshButtons();
    }

    private void openDraftFromSelection() {
        RoomState s = controller.state();
        if (!s.inRoom()) { showError("Hãy vào phòng trước."); return; }
        if (s.version() < 0 || s.token() < 0) { showError("Hãy tải code và chờ server xác nhận quyền sửa trước."); return; }
        if (!isCurrentEditor(s)) { showError("Mày cần được server cấp quyền sửa trước khi mở bản nháp."); return; }
        if (draftActive || pendingSelection != null) {
            showError("Đang có một lượt sửa. Hãy hoàn tất hoặc hủy lượt hiện tại trước."); return;
        }

        try {
            String code = codeArea.getText();
            if (code.isEmpty()) { showError("Code chung đang rỗng, không có dòng để chọn."); return; }
            int documentLength = codeArea.getDocument().getLength();
            int start = Math.min(codeArea.getSelectionStart(), documentLength);
            int end = Math.min(codeArea.getSelectionEnd(), documentLength);
            int lineCount = logicalLineCount(code);
            if (lineCount == 0) { showError("Code chung đang rỗng, không có dòng để chọn."); return; }

            int firstLine = codeArea.getLineOfOffset(start);
            int lastPosition = end > start ? end - 1 : start;
            int lastLine = codeArea.getLineOfOffset(Math.min(lastPosition, documentLength));
            firstLine = Math.max(0, Math.min(firstLine, lineCount - 1));
            lastLine = Math.max(firstLine, Math.min(lastLine, lineCount - 1));

            int lineStart = codeArea.getLineStartOffset(firstLine);
            int lineEnd = codeArea.getLineEndOffset(lastLine);
            String replacement = code.substring(Math.min(lineStart, code.length()), Math.min(lineEnd, code.length()));
            String range = (firstLine + 1) + "-" + (lastLine + 1);
            PendingSelection pending = new PendingSelection(s.version(), s.token(), range, replacement);
            pendingSelection = pending;
            status.setText("Đang yêu cầu server mở bản nháp cho dòng " + range + "...");
            controller.sendSafely(() -> controller.code().select(pending.version(), pending.token(), pending.range()));
        } catch (Exception e) {
            pendingSelection = null;
            showError("Không xác định được vùng dòng đã chọn: " + e.getMessage());
        }
    }

    /** Count lines the same way the server does: a terminal newline does not create an editable extra line. */
    private static int logicalLineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        boolean hasContentAfterLastEnding = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                count++;
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                hasContentAfterLastEnding = false;
            } else {
                hasContentAfterLastEnding = true;
            }
        }
        if (hasContentAfterLastEnding) count++;
        return count;
    }

    private void previewDraft() {
        if (!draftActive || previewOperationInProgress) return;
        String replacement = draftArea.getText();
        long token = draftToken;
        previewOperationInProgress = true;
        draftArea.setEditable(false);
        status.setText("Đang gửi vùng sửa lên server và dựng bản xem trước...");
        refreshButtons();

        Thread worker = new Thread(() -> {
            String previewResponse = null;
            String failure = null;
            try {
                String updateCommand = replacement.isEmpty()
                        ? WireCommands.rangeAction("deleterange", token, null)
                        : WireCommands.rangeAction("setrange", token, replacement);
                String accepted = controller.connection().sendAndAwait(updateCommand,
                        raw -> isDraftStatus(raw) || isServerError(raw), Duration.ofSeconds(10));
                if (!isDraftStatus(accepted)) {
                    throw new IOException(accepted);
                }
                previewResponse = controller.connection().sendAndAwait("/draft " + token,
                        raw -> raw.contains(PREVIEW_MARKER) || isServerError(raw), Duration.ofSeconds(10));
                if (!previewResponse.contains(PREVIEW_MARKER)) throw new IOException(previewResponse);
            } catch (Exception e) {
                failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }

            String finalPreviewResponse = previewResponse;
            String finalFailure = failure;
            SwingUtilities.invokeLater(() -> {
                previewOperationInProgress = false;
                draftArea.setEditable(draftActive);
                if (finalFailure == null) {
                    draftDirty = false;
                    draftAppliedToServer = true;
                    status.setText("Server đã xác nhận bản nháp. Có thể tiếp tục sửa hoặc hoàn tất lượt sửa.");
                    showPreviewDialog(finalPreviewResponse);
                } else {
                    draftAppliedToServer = false;
                    status.setText("Chưa xem được bản nháp: " + finalFailure);
                    JOptionPane.showMessageDialog(this, finalFailure, "Không thể xem bản nháp", JOptionPane.WARNING_MESSAGE);
                }
                refreshButtons();
            });
        }, "studyfix-preview-draft");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean isDraftStatus(String raw) {
        return raw != null && raw.startsWith("[Bản nháp]") && !raw.contains(PREVIEW_MARKER);
    }

    private static boolean isServerError(String raw) {
        if (raw == null) return true;
        return raw.startsWith("Từ chối") || raw.startsWith("Sai cú pháp")
                || raw.startsWith("Bạn chưa") || raw.startsWith("Chưa có bản nháp")
                || raw.startsWith("Code vượt") || raw.startsWith("Đã có bản nháp")
                || raw.startsWith("Chọn dòng hợp lệ") || raw.startsWith("Version gốc đã thay đổi");
    }

    private void showPreviewDialog(String raw) {
        if (raw == null) return;
        int marker = raw.indexOf(PREVIEW_MARKER);
        String code = marker >= 0 ? raw.substring(marker + PREVIEW_MARKER.length()).replaceFirst("^\\R", "") : raw;
        JTextArea previewText = UiStyle.monoArea();
        previewText.setEditable(false);
        previewText.setText(code);
        previewText.setCaretPosition(0);
        JScrollPane pane = new JScrollPane(previewText);
        pane.setPreferredSize(new Dimension(780, 480));
        JOptionPane.showMessageDialog(this, pane,
                "Bản xem trước toàn bộ code chưa lưu · dòng " + activeRange,
                JOptionPane.PLAIN_MESSAGE);
    }

    private void finishDraft() {
        if (!draftActive) { showError("Chưa mở bản nháp nào."); return; }
        if (previewOperationInProgress) { showError("Đang cập nhật bản nháp lên server, chờ hoàn tất rồi thử lại."); return; }
        if (!draftAppliedToServer || draftDirty) {
            showError("Bản nháp còn thay đổi chưa được server xác nhận. Bấm 'Xem bản nháp' trước khi hoàn tất.");
            return;
        }
        long token = draftToken;
        controller.sendSafely(() -> controller.code().finish(token));
        status.setText("Đã gửi yêu cầu hoàn tất. Chờ server xác nhận lưu code chung...");
    }

    private void cancelDraft() {
        if (!draftActive || previewOperationInProgress) return;
        long token = draftToken;
        controller.sendSafely(() -> controller.code().cancel(token));
        status.setText("Đã gửi yêu cầu hủy lượt sửa; chờ server xác nhận...");
    }

    private void releaseEdit() {
        if (draftActive) {
            showError("Đang có bản nháp. Hãy hoàn tất hoặc hủy lượt sửa trước khi trả quyền.");
            return;
        }
        controller.sendSafely(() -> controller.code().release(controller.state().token()));
    }

    private void wholeCodeUpdate() {
        RoomState s = controller.state();
        if (!controller.session().isTeacher()) { showError("Chỉ giáo viên mới được nạp toàn bộ code."); return; }
        if (draftActive || previewOperationInProgress) { showError("Hãy hoàn tất hoặc hủy bản nháp trước."); return; }
        JTextArea newCode = UiStyle.monoArea();
        newCode.setText(s.sharedCode());
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(newCode),
                "Nạp toàn bộ code (chỉ giáo viên chủ phòng)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            controller.sendSafely(() -> controller.code().updateWholeCode(s.version(), s.token(), newCode.getText()));
        }
    }

    private void onMessage(ServerMessage message) {
        String raw = message.raw();

        if (pendingSelection != null) {
            if (isDraftStatus(raw)) {
                activateDraft(pendingSelection);
                pendingSelection = null;
            } else if (isServerError(raw)) {
                pendingSelection = null;
                status.setText(raw);
            }
        }

        if (raw.contains("[COMMITTED]") || raw.contains("[CANCELLED]") || raw.contains("[NO_CHANGE]")) {
            boolean committed = raw.contains("[COMMITTED]");
            clearDraftState();
            status.setText(committed ? "Server đã lưu lượt sửa và cập nhật code chung." :
                    raw.contains("[CANCELLED]") ? "Đã hủy lượt sửa; code chung không thay đổi." : "Không có thay đổi; server đã kết thúc lượt sửa.");
        } else if (raw.contains("[SAVE_FAILED]")) {
            status.setText("Server chưa lưu được code; bản nháp và quyền sửa vẫn được giữ. Có thể thử hoàn tất lại.");
        } else if (message.type() == ServerMessage.Type.SHARED_CODE) {
            status.setText("Đã nhận code chung từ server. Version=" + controller.state().version());
        } else if (message.type() == ServerMessage.Type.ROOM_STATE) {
            if (!draftActive && pendingSelection == null) status.setText(raw);
        } else if (message.type() == ServerMessage.Type.DRAFT) {
            if (raw.contains(PREVIEW_MARKER)) {
                // The request handler displays the correlated full preview dialog after it has awaited the response.
                status.setText("Đã nhận dữ liệu xem trước từ server.");
            } else if (draftActive) {
                status.setText("Server đã nhận thông tin bản nháp.");
            }
        } else if (raw.startsWith("[Client]")) {
            status.setText(raw);
        }
    }

    private void activateDraft(PendingSelection pending) {
        draftActive = true;
        draftDirty = false;
        draftAppliedToServer = false;
        draftToken = pending.token();
        activeRange = pending.range();
        suppressDraftChanges = true;
        draftArea.setText(pending.originalText());
        draftArea.setCaretPosition(0);
        suppressDraftChanges = false;
        draftArea.setEditable(true);
        draftTitle.setText("Đang sửa dòng " + activeRange + " · sửa trực tiếp nội dung bên dưới; phần còn lại được giữ nguyên.");
        selectionHint.setText("Vùng dòng " + activeRange + " đã được server mở. Sửa trực tiếp trong Bản nháp, không cần bấm nút sửa từng dòng.");
        status.setText("Bản nháp đã mở. Sau khi sửa nội dung, bấm 'Xem bản nháp' để gửi lên server và xem toàn bộ code trước khi lưu.");
        refreshButtons();
    }

    private void clearDraftState() {
        draftActive = false;
        draftDirty = false;
        draftAppliedToServer = false;
        previewOperationInProgress = false;
        draftToken = -1;
        activeRange = "";
        pendingSelection = null;
        suppressDraftChanges = true;
        draftArea.setText("");
        suppressDraftChanges = false;
        draftArea.setEditable(false);
        draftTitle.setText("Chưa có lượt sửa. Xin quyền sửa trước khi mở vùng chọn.");
        selectionHint.setText("Bôi đen một hoặc nhiều dòng liên tiếp trong code chung, rồi chọn 'Mở vùng chọn thành bản nháp'.");
        refreshButtons();
    }

    private boolean isCurrentEditor(RoomState state) {
        return state.token() >= 0 && state.editorName() != null
                && state.editorName().equals(controller.session().displayName());
    }

    private void refreshFromState() {
        RoomState s = controller.state();
        stateLine.setText(s.inRoom() ? "Phòng " + s.roomId() + "  |  version=" + s.version()
                + "  |  editor=" + s.editorName() + "  |  token=" + s.token()
                + "  |  hàng đợi=" + s.queue() : "Chưa vào phòng");
        // Avoid resetting text/caret on every heartbeat/chat/state message; that would lose text selection.
        String latestCode = s.sharedCode();
        if (!codeArea.getText().equals(latestCode)) {
            int start = codeArea.getSelectionStart();
            int end = codeArea.getSelectionEnd();
            codeArea.setText(latestCode);
            codeArea.select(Math.min(start, codeArea.getDocument().getLength()),
                    Math.min(end, codeArea.getDocument().getLength()));
        }
        refreshButtons();
    }

    private void refreshButtons() {
        RoomState s = controller.state();
        boolean inRoom = s.inRoom();
        boolean editor = inRoom && isCurrentEditor(s);
        refresh.setEnabled(inRoom);
        request.setEnabled(inRoom && !draftActive && pendingSelection == null);
        release.setEnabled(editor && !draftActive && !previewOperationInProgress);
        openDraft.setEnabled(editor && !draftActive && pendingSelection == null && s.version() >= 0);
        fullUpdate.setEnabled(editor && controller.session().isTeacher() && !draftActive && !previewOperationInProgress);
        preview.setEnabled(draftActive && !previewOperationInProgress);
        finish.setEnabled(draftActive && draftAppliedToServer && !draftDirty && !previewOperationInProgress);
        cancel.setEnabled(draftActive && !previewOperationInProgress);
        draftArea.setEditable(draftActive && !previewOperationInProgress);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "StudyFix", JOptionPane.WARNING_MESSAGE);
    }

    private record PendingSelection(long version, long token, String range, String originalText) { }
}
