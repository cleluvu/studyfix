package studyfix;

import java.io.IOException;

/** Cung cấp các hàm để module client và bài tập làm việc với server. Mã phiên lấy từ kết nối đã đăng nhập. */
public final class StudyFixGateway {
    @FunctionalInterface public interface DepartureListener {
        /** Chạy trên luồng xử lý người rời phòng, không giữ khóa Room. Cần xử lý nhanh để không chặn luồng này. */
        void departed(String sessionId, Identity identity, String roomId, String reason);
    }
    private final Server server;
    StudyFixGateway(Server server) { this.server = server; }
    /** Internal service diagnostics; never pass passwords, bearer tokens or submitted source. */
    public void logService(String message) { server.log(message); }
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
    /** Trusted current classroom round; never accepts a phase or round from the browser. */
    public java.util.Map<String,Object> classroomMonitoringContext(String actor,String roomId)throws java.sql.SQLException {
        Room room=memberRoom(actor,roomId);
        synchronized(room){
            if(!room.isMember(actor))throw new SecurityException("Not a room member");
            return server.store().classroomTransaction(c->{
                try(java.sql.PreparedStatement p=c.prepareStatement("SELECT phase,round FROM classroom_sessions WHERE room_id=?")){
                    p.setString(1,roomId);try(java.sql.ResultSet r=p.executeQuery()){
                        String phase="WAITING";int round=0;if(r.next()){phase=r.getString(1);round=r.getInt(2);}
                        return studyfix.assignment.protocol.AssignmentJson.mapOf("id","classroom:"+roomId+":"+round,"roomId",roomId,"phase",phase,"round",round,"startAt",0L,"endAt",phase.equals("BRANCHING")?Long.MAX_VALUE:0L);
                    }
                }
            });
        }
    }
    /** Eligible classroom students; no IP, password or login session is exposed. */
    public java.util.List<Identity> contestStudents(String actor,String roomId)throws java.sql.SQLException{
        if(!isTeacher(actor,roomId))throw new SecurityException("Only room owner may assign exams");
        MySqlManagement db=server.store().management();
        if(db!=null)return db.transaction(c->{java.util.List<Identity> result=new java.util.ArrayList<>();
            try(java.sql.PreparedStatement p=c.prepareStatement("SELECT u.user_id,u.username,u.display_name FROM class_members m JOIN users u ON u.user_id=m.user_id JOIN classes k ON k.class_id=m.class_id WHERE m.class_id=? AND k.owner_id=? AND m.membership_status='ENROLLED' AND u.role='STUDENT' AND u.active=1")){
                p.setString(1,roomId);p.setString(2,identity(actor).userId());try(java.sql.ResultSet r=p.executeQuery()){while(r.next())result.add(new Identity(r.getString(1),r.getString(2),r.getString(3),Identity.Role.STUDENT));}
            }return result;
        });
        java.util.Map<String,Identity> result=new java.util.LinkedHashMap<>();
        for(String item:codeSnapshot(actor,roomId).members()){int split=item.indexOf(" | ");if(split<1)continue;try{Identity i=identity(item.substring(0,split));if(i.role()==Identity.Role.STUDENT)result.put(i.userId(),i);}catch(SecurityException ignored){}}
        return new java.util.ArrayList<>(result.values());
    }
    public Room.Snapshot codeSnapshot(String session, String roomId) {
        Room room = memberRoom(session, roomId);
        synchronized (room) {
            if (!room.isMember(session)) throw new SecurityException("Không thuộc phòng");
            return room.snapshot();
        }
    }
    /** Giữ khóa phòng để đọc đồng thời bản chụp code, phiên bản và trạng thái bản nháp cho nhất quán. */
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

    /** Chỉ giáo viên chủ phòng được nạp code mẫu DEBUG_COLLAB sau khi đã xác nhận rõ ràng. */
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
