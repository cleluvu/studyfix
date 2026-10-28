package studyfix;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

/**
 * Swing client for StudyFix control channel (MVP: room, chat, full-code edit).
 * Room owners submit via {@code /update}; other editors use select/setrange/finish.
 */
public final class ClientGui {
    private static final Pattern LOGIN_OK = Pattern.compile(
            "\\[LOGIN_OK] session=(\\S+) userId=(\\S+) role=(\\S+) \\| (.+)");
    private static final Pattern ROOM_STATE = Pattern.compile(
            "\\[Phòng (\\S+)] version=(\\d+), editor=([^,]+), token=(\\d+)");
    private static final Pattern JOINED = Pattern.compile("Đã (?:tạo và )?tham gia phòng: (\\S+)");
    private static final Pattern LEFT = Pattern.compile("Đã rời phòng: (\\S+)");
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "(?s)--- CODE CHUNG(?: ĐÃ CẬP NHẬT)? ---\n(.*)\\z");

    private final JFrame frame = new JFrame("StudyFix — Client");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField userField = new JTextField("teacher");
    private final JPasswordField passField = new JPasswordField();
    private final JLabel loginStatus = new JLabel(" ");

    private final JLabel status = new JLabel("Chưa kết nối");
    private final JTextField roomField = new JTextField(8);
    private final JTextArea chatLog = new JTextArea();
    private final JTextField chatInput = new JTextField();
    private final JTextArea codeArea = new JTextArea();
    private final JTextArea historyArea = new JTextArea();
    private final JButton createBtn = new JButton("Tạo phòng");
    private final JButton joinBtn = new JButton("Join");
    private final JButton leaveBtn = new JButton("Leave");
    private final JButton membersBtn = new JButton("Members");
    private final JButton requestBtn = new JButton("Xin quyền");
    private final JButton submitBtn = new JButton("Gửi code");
    private final JButton releaseBtn = new JButton("Trả quyền");
    private final JButton refreshBtn = new JButton("Làm mới code");
    private final JButton historyBtn = new JButton("Lịch sử");
    private final JButton disconnectBtn = new JButton("Ngắt kết nối");

    // Người 3 — Bài tập
    private final JTextArea assignListArea = new JTextArea();
    private final JTextField assignIdField = new JTextField(6);
    private final JTextField assignTitleField = new JTextField(18);
    private final JSpinner assignMaxSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
    private final JTextArea assignStatementArea = new JTextArea(4, 40);
    private final JTextArea assignTestsArea = new JTextArea(3, 40);
    private final JButton assignRefreshBtn = new JButton("Làm mới danh sách");
    private final JButton assignShowBtn = new JButton("Xem đề");
    private final JButton assignCreateBtn = new JButton("Giao bài");
    private final JButton assignCloseBtn = new JButton("Đóng bài");
    private final JButton submitAssignBtn = new JButton("Nộp code phòng");
    private final JButton gradesBtn = new JButton("Xem điểm nộp");
    private final JButton myScoresBtn = new JButton("Điểm của tôi");
    private final JTextField gradeSubField = new JTextField(6);
    private final JSpinner gradeScoreSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
    private final JButton gradeSetBtn = new JButton("Chấm tay");
    private final JComboBox<String> catalogTopic = new JComboBox<>(new String[]{
            "ALL", "IO", "STRING", "CLASS", "STACK", "QUEUE", "TREE"});
    private final JTextField catalogIdField = new JTextField(5);
    private final JButton catalogListBtn = new JButton("Xem ngân hàng đề");
    private final JButton catalogShowBtn = new JButton("Xem đề catalog");
    private final JButton catalogAssignBtn = new JButton("Gán đề vào phòng");

    // Người 3 — Thi & xếp hạng
    private final JTextArea examRankArea = new JTextArea();
    private final JSpinner examMinutesSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 180, 5));
    private final JTextField examTitleField = new JTextField("Kiểm tra giữa giờ", 16);
    private final JButton examStartBtn = new JButton("Bắt đầu thi");
    private final JButton examStatusBtn = new JButton("Trạng thái thi");
    private final JButton examEndBtn = new JButton("Kết thúc thi");
    private final JButton rankBtn = new JButton("Xếp hạng");

    private ClientSession session;
    private String sessionId = "";
    private String userId = "";
    private String role = "";
    private String displayName = "";
    private String roomId = "";
    private boolean roomOwner;
    private long version;
    private long token;
    private String editorName = "không có";
    private boolean holdingEdit;
    private boolean onMain;
    private String lastSharedCode = "";

    private ClientGui() {
        buildLogin();
        buildMain();
        frame.setContentPane(root);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });
        cards.show(root, "login");
        frame.setVisible(true);
    }

    private void buildLogin() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(24, 24, 24, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("IP server"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        hostField.setColumns(22); form.add(hostField, c);
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(new JLabel("Username"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        form.add(userField, c);
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        form.add(new JLabel("Password"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        form.add(passField, c);
        JButton connect = new JButton("Kết nối");
        c.gridx = 1; c.gridy = 3; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
        form.add(connect, c);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST;
        loginStatus.setForeground(new Color(0xB00020));
        form.add(loginStatus, c);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(new JLabel("StudyFix Client", SwingConstants.CENTER), BorderLayout.NORTH);
        wrap.add(form, BorderLayout.CENTER);
        root.add(wrap, "login");

        connect.addActionListener(e -> connect());
        passField.addActionListener(e -> connect());
    }

    private void buildMain() {
        chatLog.setEditable(false);
        chatLog.setLineWrap(true);
        chatLog.setWrapStyleWord(true);
        ((DefaultCaret) chatLog.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        historyArea.setEditable(false);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        codeArea.setEditable(false);

        JPanel roomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        roomBar.add(new JLabel("Mã phòng"));
        roomBar.add(roomField);
        roomBar.add(createBtn);
        roomBar.add(joinBtn);
        roomBar.add(leaveBtn);
        roomBar.add(membersBtn);
        roomBar.add(disconnectBtn);

        JPanel codeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        codeBar.add(requestBtn);
        codeBar.add(submitBtn);
        codeBar.add(releaseBtn);
        codeBar.add(refreshBtn);
        codeBar.add(historyBtn);

        JPanel chatPane = new JPanel(new BorderLayout(4, 4));
        chatPane.setBorder(BorderFactory.createTitledBorder("Chat / nhật ký"));
        chatPane.add(new JScrollPane(chatLog), BorderLayout.CENTER);
        JPanel sendRow = new JPanel(new BorderLayout(4, 0));
        JButton sendChat = new JButton("Gửi");
        sendRow.add(chatInput, BorderLayout.CENTER);
        sendRow.add(sendChat, BorderLayout.EAST);
        chatPane.add(sendRow, BorderLayout.SOUTH);

        JPanel codePane = new JPanel(new BorderLayout(4, 4));
        codePane.setBorder(BorderFactory.createTitledBorder("Code chung"));
        codePane.add(codeBar, BorderLayout.NORTH);
        codePane.add(new JScrollPane(codeArea), BorderLayout.CENTER);

        JSplitPane mid = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatPane, codePane);
        mid.setResizeWeight(0.4);

        JTabbedPane bottom = new JTabbedPane();
        bottom.addTab("Lịch sử / phản hồi dài", new JScrollPane(historyArea));
        bottom.addTab("Bài tập & nộp bài", buildAssignTab());
        bottom.addTab("Thi & xếp hạng", buildExamRankTab());

        JSplitPane body = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mid, bottom);
        body.setResizeWeight(0.62);

        JPanel top = new JPanel(new BorderLayout(8, 4));
        top.setBorder(new EmptyBorder(8, 10, 4, 10));
        top.add(status, BorderLayout.CENTER);
        top.add(roomBar, BorderLayout.SOUTH);

        JPanel main = new JPanel(new BorderLayout());
        main.add(top, BorderLayout.NORTH);
        main.add(body, BorderLayout.CENTER);
        root.add(main, "main");

        createBtn.addActionListener(e -> command("/create"));
        joinBtn.addActionListener(e -> {
            String id = roomField.getText().trim();
            if (id.isEmpty()) { appendChat("Nhập mã phòng trước khi Join."); return; }
            command("/join " + id);
        });
        leaveBtn.addActionListener(e -> command("/leave"));
        membersBtn.addActionListener(e -> command("/members"));
        requestBtn.addActionListener(e -> command("/request"));
        refreshBtn.addActionListener(e -> command("/code"));
        historyBtn.addActionListener(e -> command("/history"));
        releaseBtn.addActionListener(e -> {
            if (!holdingEdit) { appendChat("Bạn không đang giữ quyền sửa."); return; }
            command("/release " + token);
        });
        submitBtn.addActionListener(e -> submitCode());
        sendChat.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());
        disconnectBtn.addActionListener(e -> {
            if (session != null) session.disconnect();
            showLogin("Đã ngắt kết nối.");
        });
        wirePerson3Actions();
        refreshEditControls();
    }

    private JPanel buildAssignTab() {
        assignListArea.setEditable(false);
        assignListArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        assignStatementArea.setLineWrap(true);
        assignStatementArea.setWrapStyleWord(true);
        assignTestsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        assignTestsArea.setToolTipText("Mỗi test: stdin>>>stdout — cách nhau bằng ;;");

        JPanel teacher = new JPanel(new GridBagLayout());
        teacher.setBorder(BorderFactory.createTitledBorder("Giáo viên chủ phòng — giao bài"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; teacher.add(new JLabel("Tiêu đề"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        teacher.add(assignTitleField, c);
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        teacher.add(new JLabel("Điểm tối đa"), c);
        c.gridx = 3; teacher.add(assignMaxSpinner, c);
        c.gridx = 0; c.gridy = 1; teacher.add(new JLabel("Đề bài"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.BOTH; c.weightx = 1; c.weighty = 1;
        teacher.add(new JScrollPane(assignStatementArea), c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weighty = 0; c.fill = GridBagConstraints.NONE;
        teacher.add(new JLabel("Tests"), c);
        c.gridx = 1; c.gridwidth = 3; c.fill = GridBagConstraints.BOTH; c.weighty = 0.6;
        teacher.add(new JScrollPane(assignTestsArea), c);
        c.gridx = 1; c.gridy = 3; c.gridwidth = 3; c.fill = GridBagConstraints.NONE; c.weighty = 0;
        JPanel teacherBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        teacherBtns.add(assignCreateBtn);
        teacherBtns.add(assignCloseBtn);
        teacherBtns.add(new JLabel("Mã bài"));
        teacherBtns.add(assignIdField);
        teacherBtns.add(gradeSetBtn);
        teacherBtns.add(new JLabel("nộp#"));
        teacherBtns.add(gradeSubField);
        teacherBtns.add(new JLabel("điểm"));
        teacherBtns.add(gradeScoreSpinner);
        teacher.add(teacherBtns, c);

        JPanel catalog = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        catalog.setBorder(BorderFactory.createTitledBorder(
                "Ngân hàng đề — IO / STRING / CLASS / STACK / QUEUE / TREE"));
        catalog.add(new JLabel("Chủ đề"));
        catalog.add(catalogTopic);
        catalog.add(catalogListBtn);
        catalog.add(new JLabel("ID catalog"));
        catalog.add(catalogIdField);
        catalog.add(catalogShowBtn);
        catalog.add(catalogAssignBtn);

        JPanel student = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        student.setBorder(BorderFactory.createTitledBorder("Học sinh / chung"));
        student.add(assignRefreshBtn);
        student.add(assignShowBtn);
        student.add(submitAssignBtn);
        student.add(gradesBtn);
        student.add(myScoresBtn);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(catalog, BorderLayout.NORTH);
        north.add(teacher, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(north, BorderLayout.CENTER);
        left.add(student, BorderLayout.SOUTH);

        JPanel wrap = new JPanel(new BorderLayout(6, 6));
        wrap.setBorder(new EmptyBorder(6, 8, 6, 8));
        wrap.add(left, BorderLayout.NORTH);
        wrap.add(new JScrollPane(assignListArea), BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildExamRankTab() {
        examRankArea.setEditable(false);
        examRankArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(new JLabel("Phút"));
        bar.add(examMinutesSpinner);
        bar.add(new JLabel("Tiêu đề"));
        bar.add(examTitleField);
        bar.add(examStartBtn);
        bar.add(examStatusBtn);
        bar.add(examEndBtn);
        bar.add(rankBtn);
        JPanel wrap = new JPanel(new BorderLayout(4, 4));
        wrap.setBorder(new EmptyBorder(6, 8, 6, 8));
        wrap.add(bar, BorderLayout.NORTH);
        wrap.add(new JScrollPane(examRankArea), BorderLayout.CENTER);
        return wrap;
    }

    private void wirePerson3Actions() {
        assignRefreshBtn.addActionListener(e -> command("/assign list"));
        assignShowBtn.addActionListener(e -> {
            String id = assignIdField.getText().trim();
            if (id.isEmpty()) { appendChat("Nhập mã bài tập."); return; }
            command("/assign show " + id);
        });
        assignCreateBtn.addActionListener(e -> createAssignment());
        assignCloseBtn.addActionListener(e -> {
            String id = assignIdField.getText().trim();
            if (id.isEmpty()) { appendChat("Nhập mã bài để đóng."); return; }
            command("/assign close " + id);
        });
        submitAssignBtn.addActionListener(e -> {
            String id = assignIdField.getText().trim();
            if (id.isEmpty()) { appendChat("Nhập mã bài để nộp."); return; }
            command("/submit " + id);
        });
        gradesBtn.addActionListener(e -> {
            String id = assignIdField.getText().trim();
            command(id.isEmpty() ? "/grades" : "/grades " + id);
        });
        myScoresBtn.addActionListener(e -> command("/myscores"));
        gradeSetBtn.addActionListener(e -> {
            String sid = gradeSubField.getText().trim();
            if (sid.isEmpty()) { appendChat("Nhập mã bài nộp để chấm tay."); return; }
            command("/grade set " + sid + " " + gradeScoreSpinner.getValue());
        });
        examStartBtn.addActionListener(e -> {
            String title = examTitleField.getText().trim();
            String cmd = "/exam start " + examMinutesSpinner.getValue();
            if (!title.isEmpty()) cmd += " " + title;
            command(cmd);
        });
        examStatusBtn.addActionListener(e -> command("/exam status"));
        examEndBtn.addActionListener(e -> command("/exam end"));
        rankBtn.addActionListener(e -> command("/rank"));
        catalogListBtn.addActionListener(e -> {
            String topic = String.valueOf(catalogTopic.getSelectedItem());
            command("ALL".equals(topic) ? "/catalog list" : "/catalog list " + topic);
        });
        catalogShowBtn.addActionListener(e -> {
            String id = catalogIdField.getText().trim();
            if (id.isEmpty()) { appendChat("Nhập ID đề trong ngân hàng."); return; }
            command("/catalog show " + id);
        });
        catalogAssignBtn.addActionListener(e -> {
            String id = catalogIdField.getText().trim();
            if (id.isEmpty()) { appendChat("Nhập ID đề catalog để gán vào phòng."); return; }
            command("/catalog assign " + id);
        });
    }

    private void createAssignment() {
        String title = assignTitleField.getText().trim();
        String statement = assignStatementArea.getText().trim();
        String tests = assignTestsArea.getText().trim();
        if (title.isEmpty() || statement.isEmpty()) {
            appendChat("Nhập tiêu đề và đề bài trước khi giao.");
            return;
        }
        // Escape: protocol uses | as separator — reject if user puts bare pipes in title line oddly
        if (title.contains("|") || statement.contains("|")) {
            appendChat("Tiêu đề/đề bài không được chứa ký tự | (dùng để tách trường).");
            return;
        }
        StringBuilder cmd = new StringBuilder("/assign create ")
                .append(assignMaxSpinner.getValue()).append(' ').append(title)
                .append(" | ").append(statement);
        if (!tests.isEmpty()) cmd.append(" | ").append(tests.replace('\n', ' ').trim());
        command(cmd.toString());
    }

    private void connect() {
        String host = hostField.getText().trim();
        if (host.isEmpty()) host = "127.0.0.1";
        String user = userField.getText().trim();
        char[] passChars = passField.getPassword();
        String password = new String(passChars);
        java.util.Arrays.fill(passChars, '\0');
        if (user.isEmpty() || password.isEmpty()) {
            loginStatus.setText("Nhập username và password.");
            return;
        }
        loginStatus.setText("Đang kết nối…");
        loginStatus.setForeground(Color.DARK_GRAY);
        String hostFinal = host;
        new SwingWorker<ClientSession, Void>() {
            private String fail;

            @Override protected ClientSession doInBackground() {
                try {
                    int port = Integer.getInteger("studyfix.port", 5000);
                    return ClientSession.connect(hostFinal, port, user, password, new ClientSession.Listener() {
                        @Override public void onMessage(String text) {
                            SwingUtilities.invokeLater(() -> handleMessage(text));
                        }
                        @Override public void onDisconnected(String reason) {
                            SwingUtilities.invokeLater(() -> onSessionEnded(reason));
                        }
                    });
                } catch (IOException e) {
                    fail = e.getMessage();
                    return null;
                }
            }

            @Override protected void done() {
                try {
                    ClientSession s = get();
                    if (s == null) {
                        loginStatus.setForeground(new Color(0xB00020));
                        loginStatus.setText(fail == null ? "Không kết nối được." : fail);
                        return;
                    }
                    session = s;
                    passField.setText("");
                    chatLog.setText("");
                    historyArea.setText("");
                    assignListArea.setText("");
                    examRankArea.setText("");
                    codeArea.setText("");
                    roomId = "";
                    roomOwner = false;
                    holdingEdit = false;
                    version = 0;
                    token = 0;
                    onMain = true;
                    cards.show(root, "main");
                    refreshStatus();
                    refreshEditControls();
                    appendChat("Đã kết nối tới " + hostFinal + ".");
                } catch (Exception e) {
                    loginStatus.setForeground(new Color(0xB00020));
                    loginStatus.setText(e.getMessage());
                }
            }
        }.execute();
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        chatInput.setText("");
        command("/chat " + text);
    }

    private void submitCode() {
        if (session == null) return;
        if (roomId.isEmpty()) { appendChat("Chưa ở trong phòng."); return; }
        if (!holdingEdit) { appendChat("Xin quyền sửa trước khi gửi code."); return; }
        String code = codeArea.getText();
        if (code.trim().isEmpty()) { appendChat("Code trống — không gửi."); return; }
        if (code.getBytes(StandardCharsets.UTF_8).length > 32000) {
            appendChat("Code vượt 32000 byte UTF-8.");
            return;
        }
        try {
            if (roomOwner) {
                session.send("/update " + version + " " + token + " " + code);
                appendChat("Đã gửi /update (chủ phòng).");
            } else {
                // Server indexes lines of the shared snapshot, not the local edit buffer.
                int sharedLines = countLogicalLines(lastSharedCode.isEmpty() ? code : lastSharedCode);
                if (sharedLines < 1) {
                    appendChat("Code chung trống — chỉ chủ phòng dùng /update được. Hãy nhờ giáo viên nạp code trước.");
                    return;
                }
                session.send("/select " + version + " " + token + " 1-" + sharedLines);
                session.send("/setrange " + token + " " + code);
                session.send("/finish " + token);
                appendChat("Đã gửi lượt sửa (select/setrange/finish).");
            }
        } catch (IOException e) {
            appendChat("Không gửi được: " + e.getMessage());
        }
    }

    private static int countLogicalLines(String code) {
        if (code == null || code.isEmpty()) return 0;
        return CodeDiff.lines(code).size();
    }

    private void command(String cmd) {
        if (session == null) return;
        try {
            session.send(cmd);
        } catch (IOException e) {
            appendChat("Không gửi được: " + e.getMessage());
        }
    }

    private void handleMessage(String text) {
        Matcher login = LOGIN_OK.matcher(text);
        if (login.find()) {
            sessionId = login.group(1);
            userId = login.group(2);
            role = login.group(3);
            displayName = login.group(4).trim();
            refreshStatus();
            appendChat(text);
            return;
        }
        Matcher joined = JOINED.matcher(text);
        if (joined.find()) {
            roomId = joined.group(1);
            roomOwner = text.startsWith("Đã tạo");
            roomField.setText(roomId);
            holdingEdit = false;
            refreshStatus();
            refreshEditControls();
            appendChat(text);
            // Code snapshot usually follows in the same or next messages.
            maybeAbsorbCode(text);
            return;
        }
        if (LEFT.matcher(text).find()) {
            roomId = "";
            roomOwner = false;
            holdingEdit = false;
            lastSharedCode = "";
            refreshStatus();
            refreshEditControls();
            appendChat(text);
            return;
        }
        if (text.startsWith("[Lịch sử") || text.startsWith("[Xem lịch sử") || text.startsWith("[Bản nháp]")) {
            historyArea.append(text);
            if (!text.endsWith("\n")) historyArea.append("\n");
            historyArea.append("\n");
            appendChat("(Đã ghi lịch sử/bản nháp vào tab dưới.)");
            parseRoomState(text);
            return;
        }
        if (isPerson3Payload(text)) {
            routePerson3Message(text);
            appendChat(summarizePerson3(text));
            refreshEditControls();
            refreshStatus();
            return;
        }
        parseRoomState(text);
        maybeAbsorbCode(text);
        if (text.contains("[Phòng ") && text.contains(": ") && !text.contains("version=")) {
            appendChat(text); // chat broadcast style
        } else if (!text.contains("--- CODE CHUNG")) {
            appendChat(text);
        } else {
            appendChat(summarizeCodeMessage(text));
        }
        refreshEditControls();
        refreshStatus();
    }

    private static boolean isPerson3Payload(String text) {
        return text.startsWith("[ASSIGN") || text.startsWith("[SUBMIT")
                || text.startsWith("[GRADE") || text.startsWith("[GRADES]")
                || text.startsWith("[EXAM]") || text.startsWith("[RANK]")
                || text.startsWith("[MYSCORES]") || text.startsWith("[P3_ERROR]");
    }

    private void routePerson3Message(String text) {
        if (text.startsWith("[ASSIGN_LIST]") || text.startsWith("[ASSIGN]")
                || text.startsWith("[GRADES]") || text.startsWith("[MYSCORES]")
                || text.startsWith("[SUBMIT_OK]") || text.startsWith("[GRADE_RESULT]")) {
            assignListArea.append(text);
            if (!text.endsWith("\n")) assignListArea.append("\n");
            assignListArea.append("\n");
            if (text.startsWith("[ASSIGN] id=")) {
                int idAt = text.indexOf("id=");
                if (idAt >= 0) {
                    String rest = text.substring(idAt + 3).trim();
                    String id = rest.split("[\\s]", 2)[0];
                    assignIdField.setText(id);
                }
            }
        }
        if (text.startsWith("[EXAM]") || text.startsWith("[RANK]") || text.startsWith("[GRADE_RESULT]")) {
            examRankArea.append(text);
            if (!text.endsWith("\n")) examRankArea.append("\n");
            examRankArea.append("\n");
        }
        if (text.startsWith("[P3_ERROR]")) {
            assignListArea.append(text + "\n");
        }
    }

    private static String summarizePerson3(String text) {
        String first = text.lines().findFirst().orElse(text);
        if (first.length() > 120) first = first.substring(0, 120) + "…";
        return first + " (chi tiết ở tab Bài tập / Thi)";
    }

    private String summarizeCodeMessage(String text) {
        Matcher st = ROOM_STATE.matcher(text);
        return st.find()
                ? st.group(0) + " (đã cập nhật khung code)"
                : "Đã nhận bản code chung.";
    }

    private void parseRoomState(String text) {
        Matcher st = ROOM_STATE.matcher(text);
        if (!st.find()) return;
        roomId = st.group(1);
        version = Long.parseLong(st.group(2));
        editorName = st.group(3).trim();
        token = Long.parseLong(st.group(4));
        holdingEdit = !displayName.isEmpty() && displayName.equals(editorName);
    }

    private void maybeAbsorbCode(String text) {
        Matcher block = CODE_BLOCK.matcher(text);
        if (!block.find()) return;
        String code = block.group(1);
        if (code.endsWith("\n") && text.contains("ĐÃ CẬP NHẬT")) {
            // keep as-is
        }
        lastSharedCode = code;
        boolean wasEditable = codeArea.isEditable();
        int caret = codeArea.getCaretPosition();
        codeArea.setText(code);
        codeArea.setCaretPosition(Math.min(caret, codeArea.getDocument().getLength()));
        codeArea.setEditable(wasEditable);
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(displayName.isEmpty() ? "…" : displayName);
        if (!role.isEmpty()) sb.append(" | ").append(role);
        if (!sessionId.isEmpty()) sb.append(" | session=").append(shortId(sessionId));
        if (roomId.isEmpty()) sb.append(" | chưa vào phòng");
        else {
            sb.append(" | phòng ").append(roomId);
            if (roomOwner) sb.append(" (chủ)");
            sb.append(" | version=").append(version);
            sb.append(" | token=").append(token);
            sb.append(" | editor=").append(editorName);
            if (holdingEdit) sb.append(" | ĐANG GIỮ QUYỀN");
        }
        status.setText(sb.toString());
    }

    private static String shortId(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8) + "…";
    }

    private void refreshEditControls() {
        boolean inRoom = !roomId.isEmpty();
        // Nút giao bài/thi hiện với mọi TEACHER; server chỉ cho chủ phòng.
        boolean teacherInRoom = inRoom && "TEACHER".equals(role);
        createBtn.setEnabled(session != null && !inRoom && "TEACHER".equals(role));
        joinBtn.setEnabled(session != null && !inRoom);
        leaveBtn.setEnabled(session != null && inRoom);
        membersBtn.setEnabled(session != null && inRoom);
        requestBtn.setEnabled(session != null && inRoom);
        refreshBtn.setEnabled(session != null && inRoom);
        historyBtn.setEnabled(session != null && inRoom);
        submitBtn.setEnabled(session != null && inRoom && holdingEdit);
        releaseBtn.setEnabled(session != null && inRoom && holdingEdit);
        codeArea.setEditable(inRoom && holdingEdit);

        assignRefreshBtn.setEnabled(session != null && inRoom);
        assignShowBtn.setEnabled(session != null && inRoom);
        submitAssignBtn.setEnabled(session != null && inRoom);
        gradesBtn.setEnabled(session != null && inRoom);
        myScoresBtn.setEnabled(session != null && inRoom);
        assignCreateBtn.setEnabled(session != null && teacherInRoom);
        assignCloseBtn.setEnabled(session != null && teacherInRoom);
        gradeSetBtn.setEnabled(session != null && teacherInRoom);
        examStartBtn.setEnabled(session != null && teacherInRoom);
        examEndBtn.setEnabled(session != null && teacherInRoom);
        examStatusBtn.setEnabled(session != null && inRoom);
        rankBtn.setEnabled(session != null && inRoom);
    }

    private void appendChat(String line) {
        if (line == null || line.isEmpty()) return;
        chatLog.append(line);
        if (!line.endsWith("\n")) chatLog.append("\n");
    }

    private void onSessionEnded(String reason) {
        session = null;
        holdingEdit = false;
        if (frame.isDisplayable() && onMain) showLogin(reason);
    }

    private void showLogin(String message) {
        if (session != null) {
            session.disconnect();
            session = null;
        }
        onMain = false;
        roomId = "";
        roomOwner = false;
        holdingEdit = false;
        sessionId = userId = role = displayName = "";
        loginStatus.setForeground(new Color(0xB00020));
        loginStatus.setText(message == null ? " " : message);
        cards.show(root, "login");
    }

    private void shutdown() {
        if (session != null) session.disconnect();
        frame.dispose();
        System.exit(0);
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(ClientGui::new);
    }
}
