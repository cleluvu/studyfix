package vn.studyfix.client.service;

import vn.studyfix.client.net.TcpControlConnection;
import vn.studyfix.client.protocol.WireCommands;
import java.io.IOException;

/** Commands mirror the server's editing state machine. Client never decides that an edit succeeded. */
public final class CodeCollaborationService {
    private final TcpControlConnection connection;
    public CodeCollaborationService(TcpControlConnection connection) { this.connection = connection; }
    private void send(String command) throws IOException { connection.send(command); }
    public void refreshCode() throws IOException { send("/code"); }
    public void refreshNumberedCode() throws IOException { send("/lines"); }
    public void requestEdit() throws IOException { send("/request"); }
    public void release(long token) throws IOException { if (token < 0) throw new IllegalArgumentException("Chưa có token hợp lệ."); send("/release " + token); }
    public void history() throws IOException { send("/history"); }
    public void history(long version) throws IOException { if (version < 0) throw new IllegalArgumentException("Version phải không âm."); send("/history " + version); }
    public void select(long version, long token, String selection) throws IOException { send(WireCommands.select(version, token, selection)); }
    public void setLine(long token, int line, String text) throws IOException { send(WireCommands.setLine(token, line, text)); }
    public void deleteLine(long token, int line) throws IOException { send(WireCommands.lineAction("delete", token, line)); }
    public void keepLine(long token, int line) throws IOException { send(WireCommands.lineAction("keep", token, line)); }
    public void setRange(long token, String text) throws IOException { send(WireCommands.rangeAction("setrange", token, text)); }
    public void deleteRange(long token) throws IOException { send(WireCommands.rangeAction("deleterange", token, null)); }
    public void keepRange(long token) throws IOException { send(WireCommands.rangeAction("keeprange", token, null)); }
    public void previewDraft(long token) throws IOException { send("/draft " + checkedToken(token)); }
    public void finish(long token) throws IOException { send("/finish " + checkedToken(token)); }
    public void cancel(long token) throws IOException { send("/cancel " + checkedToken(token)); }
    public void updateWholeCode(long version, long token, String code) throws IOException { send(WireCommands.update(version, token, code)); }
    private long checkedToken(long token) { if (token < 0) throw new IllegalArgumentException("Chưa có token hợp lệ."); return token; }
}
