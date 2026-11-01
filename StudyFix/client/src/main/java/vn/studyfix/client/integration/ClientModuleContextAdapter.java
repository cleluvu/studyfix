package vn.studyfix.client.integration;

import vn.studyfix.client.controller.RoomController;
import vn.studyfix.client.model.RoomState;
import vn.studyfix.client.model.SessionInfo;
import vn.studyfix.client.protocol.ServerMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/** Adapts the shared network/session core for an independently-developed member-3 module. */
public final class ClientModuleContextAdapter implements ClientModuleContext {
    private final RoomController controller;
    public ClientModuleContextAdapter(RoomController controller) { this.controller = Objects.requireNonNull(controller); }
    @Override public SessionInfo session() { return controller.session(); }
    @Override public RoomState roomState() { return controller.state(); }
    @Override public void sendControlCommand(String command) {
        try {
            if (command == null || command.isBlank()) throw new IllegalArgumentException("Lệnh rỗng.");
            if (command.getBytes(StandardCharsets.UTF_8).length > 65_536) throw new IllegalArgumentException("Lệnh vượt giới hạn frame 64 KiB.");
            controller.connection().send(command);
        } catch (IOException | IllegalArgumentException e) { controller.notifyLocalError(e.getMessage()); }
    }
    @Override public void addMessageListener(Consumer<ServerMessage> listener) { controller.addMessageObserver(listener); }
    @Override public void addStateListener(Runnable listener) { controller.addStateObserver(listener); }
    @Override public void reportClientError(String message) { controller.notifyLocalError(message); }
}
