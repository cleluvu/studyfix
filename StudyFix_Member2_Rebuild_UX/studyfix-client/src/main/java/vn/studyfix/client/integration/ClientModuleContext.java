package vn.studyfix.client.integration;

import vn.studyfix.client.model.RoomState;
import vn.studyfix.client.model.SessionInfo;
import vn.studyfix.client.protocol.ServerMessage;

import java.io.IOException;
import java.util.function.Consumer;

/** Stable integration surface for member 3's Exercise/Submission/Contest panels. */
public interface ClientModuleContext {
    SessionInfo session();
    RoomState roomState();
    void sendControlCommand(String command);
    void addMessageListener(Consumer<ServerMessage> listener);
    void addStateListener(Runnable listener);
    void reportClientError(String message);
}
