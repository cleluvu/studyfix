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
    /** Snapshot/version/draft flags read atomically through the core room lock. */
    public record AssignmentCodeSnapshot(String roomId, String code, long version,
                                         boolean draftActive, boolean editTurnActive) { }

    public AssignmentCodeSnapshot assignmentCodeSnapshot(String session, String roomId) {
        Room room = memberRoom(session, roomId);
        synchronized (room) {
            if (!room.isMember(session)) throw new SecurityException("Không thuộc phòng");
            Room.Snapshot s = room.snapshot();
            return new AssignmentCodeSnapshot(s.roomId(), s.code(), s.version(),
                    room.hasActiveDraft(), room.hasActiveEditTurn());
        }
    }

    /** Teacher-owner-only write gate for explicitly confirmed DEBUG_COLLAB starter loading. */
    public long loadAssignmentStarterCode(String session, String roomId, long expectedVersion,
                                          String starterCode, String assignmentTitle) {
        Room room = memberRoom(session, roomId);
        ClientHandler actor = session(session);
        synchronized (room) {
            if (!room.isMember(session) || !room.isOwner(actor.identity()))
                throw new SecurityException("Chỉ giáo viên chủ phòng mới được nạp code lỗi mẫu.");
            try { return room.loadAssignmentStarter(actor, expectedVersion, starterCode, assignmentTitle); }
            catch (IOException e) { throw new IllegalStateException("Không thể phát code mẫu tới phòng.", e); }
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
