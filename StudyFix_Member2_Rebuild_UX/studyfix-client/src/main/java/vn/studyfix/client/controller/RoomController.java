package vn.studyfix.client.controller;

import vn.studyfix.client.model.MemberInfo;
import vn.studyfix.client.model.RoomState;
import vn.studyfix.client.model.SessionInfo;
import vn.studyfix.client.net.TcpControlConnection;
import vn.studyfix.client.protocol.ServerMessage;
import vn.studyfix.client.service.CodeCollaborationService;
import vn.studyfix.client.service.RoomService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** UI-facing coordinator. Server events update this view model; they do not grant local permission. */
public final class RoomController {
    private static final Pattern ROOM_JOINED = Pattern.compile("(?:Đã tạo và tham gia phòng|Đã tham gia phòng):\\s*(\\S+)");
    private static final Pattern ROOM_LEFT = Pattern.compile("Đã rời phòng:\\s*(\\S+)");
    private final TcpControlConnection connection;
    private final SessionInfo session;
    private final RoomService rooms;
    private final CodeCollaborationService code;
    private final RoomState state = new RoomState();
    private final CopyOnWriteArrayList<Consumer<ServerMessage>> messageObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> stateObservers = new CopyOnWriteArrayList<>();

    public RoomController(TcpControlConnection connection, SessionInfo session) {
        this.connection = connection; this.session = session;
        this.rooms = new RoomService(connection); this.code = new CodeCollaborationService(connection);
    }
    public SessionInfo session() { return session; }
    public RoomState state() { return state; }
    public RoomService rooms() { return rooms; }
    public CodeCollaborationService code() { return code; }
    public TcpControlConnection connection() { return connection; }
    public void addMessageObserver(Consumer<ServerMessage> observer) { messageObservers.add(observer); }
    public void addStateObserver(Runnable observer) { stateObservers.add(observer); }

    public void accept(ServerMessage message) {
        String raw = message.raw();
        if (message.hasRoomState()) {
            state.setRoomId(message.roomId()); state.setVersion(message.version());
            state.setEditorName(message.editorName()); state.setToken(message.token()); state.setQueue(message.queue());
        }
        if (message.type() == ServerMessage.Type.SHARED_CODE) {
            if (message.roomId() != null) state.setRoomId(message.roomId());
            if (message.version() != null) state.setVersion(message.version());
            if (message.editorName() != null) state.setEditorName(message.editorName());
            if (message.token() != null) state.setToken(message.token());
            if (message.queue() != null) state.setQueue(message.queue());
            state.setSharedCode(message.code());
        }
        if (message.type() == ServerMessage.Type.MEMBERS) {
            List<MemberInfo> mapped = new ArrayList<>();
            for (String item : message.memberNames()) {
                int marker = item.lastIndexOf("  ·  ");
                if (marker > 0) mapped.add(new MemberInfo(item.substring(marker + 5).trim(), item.substring(0, marker).trim()));
                else mapped.add(new MemberInfo(null, item.trim()));
            }
            state.setMembers(mapped);
        }
        if (message.type() == ServerMessage.Type.DRAFT) state.setLastDraftStatus(raw);
        if (message.type() == ServerMessage.Type.HISTORY) state.setLastHistory(raw);

        Matcher joined = ROOM_JOINED.matcher(raw);
        if (joined.find()) state.setRoomId(joined.group(1));
        Matcher left = ROOM_LEFT.matcher(raw);
        if (left.find()) state.clearRoom();
        if (raw.contains("Bạn chưa tham gia phòng.")) {
            // Don't clear state on every command error; this message specifically indicates not joined.
        }
        for (Consumer<ServerMessage> observer : messageObservers) {
            try { observer.accept(message); } catch (RuntimeException ignored) { }
        }
        for (Runnable observer : stateObservers) {
            try { observer.run(); } catch (RuntimeException ignored) { }
        }
    }

    public void sendSafely(ThrowingAction action) {
        try { action.run(); }
        catch (IOException | IllegalArgumentException e) { notifyLocalError(e.getMessage()); }
    }
    public void notifyLocalError(String text) {
        ServerMessage event = new ServerMessage(ServerMessage.Type.GENERIC, "[Client] " + text,
                null, null, null, null, List.of(), null, List.of(), null, null, null, null);
        for (Consumer<ServerMessage> observer : messageObservers) {
            try { observer.accept(event); } catch (RuntimeException ignored) { }
        }
    }
    @FunctionalInterface public interface ThrowingAction { void run() throws IOException; }
}
