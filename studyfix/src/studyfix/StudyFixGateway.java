package studyfix;

import java.io.IOException;

/** Integration surface for teammates 2/3. Session IDs come from authenticated sockets. */
public final class StudyFixGateway {
    @FunctionalInterface public interface DepartureListener {
        /** Runs on the departing worker, outside Room locks. Must return promptly. */
        void departed(String sessionId, Identity identity, String roomId, String reason);
    }
    private final Server server;
    StudyFixGateway(Server server) { this.server = server; }
    public Identity identity(String sessionId) { return session(sessionId).identity(); }
    private ClientHandler session(String id) {
        ClientHandler client = server.clients.get(id);
        if (client == null || !client.isConnected() || client.identity() == null) throw new SecurityException("Phiên chưa đăng nhập");
        return client;
    }
    private Room memberRoom(String session, String roomId) {
        session(session);
        Room room = server.rooms.get(roomId);
        if (room == null || !room.isMember(session)) throw new SecurityException("Không thuộc phòng");
        return room;
    }
    public boolean isMember(String session, String roomId) {
        Room room = server.rooms.get(roomId);
        return room != null && server.clients.containsKey(session) && room.isMember(session);
    }
    public boolean isTeacher(String session, String roomId) {
        Room room = memberRoom(session, roomId);
        synchronized (room) { return room.isMember(session) && room.isOwner(identity(session)); }
    }
    public Room.Snapshot codeSnapshot(String session, String roomId) {
        Room room = memberRoom(session, roomId);
        synchronized (room) {
            if (!room.isMember(session)) throw new SecurityException("Không thuộc phòng");
            return room.snapshot();
        }
    }
    public boolean canEdit(String session, String roomId, long token) { return memberRoom(session, roomId).canEdit(session, token); }
    public void sendToUser(String actor, String recipient, String roomId, String event) throws IOException {
        Room room = memberRoom(actor, roomId);
        synchronized (room) {
            if (!room.isMember(actor) || !room.isMember(recipient)) throw new SecurityException("Không thuộc cùng phòng");
            session(recipient).send(event);
        }
    }
    public void sendToRoom(String actor, String roomId, String event) {
        Room room = memberRoom(actor, roomId);
        synchronized (room) {
            if (!room.isMember(actor)) throw new SecurityException("Không thuộc phòng");
            room.broadcast(event);
        }
    }
    public void addDepartureListener(DepartureListener listener) { server.addDepartureListener(listener); }
    public void removeDepartureListener(DepartureListener listener) { server.removeDepartureListener(listener); }
}
