package vn.studyfix.client.service;

import vn.studyfix.client.net.TcpControlConnection;
import vn.studyfix.client.protocol.WireCommands;
import java.io.IOException;

/** Thin adapter: one command per operation, using only commands present on the server today. */
public final class RoomService {
    private final TcpControlConnection connection;
    public RoomService(TcpControlConnection connection) { this.connection = connection; }
    private void send(String command) throws IOException { connection.send(command); }
    public void create() throws IOException { send("/create"); }
    public void join(String roomId) throws IOException { send(WireCommands.join(roomId)); }
    public void leave() throws IOException { send("/leave"); }
    public void requestMembers() throws IOException { send("/members"); }
    public void kick(String sessionId) throws IOException {
        if (sessionId == null || sessionId.isBlank() || sessionId.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("Session ID không hợp lệ.");
        send("/kick " + sessionId.trim());
    }
    public void chat(String text) throws IOException { send(WireCommands.chat(text)); }
}
