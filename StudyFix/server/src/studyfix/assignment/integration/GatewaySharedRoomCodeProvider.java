package studyfix.assignment.integration;

import studyfix.StudyFixGateway;

import java.util.Objects;

/** Kết nối module bài tập với phần lõi; mọi thao tác đọc và ghi đều đi qua gateway. */
public final class GatewaySharedRoomCodeProvider implements SharedRoomCodeProvider {
    private final StudyFixGateway gateway;
    public GatewaySharedRoomCodeProvider(StudyFixGateway gateway) { this.gateway = Objects.requireNonNull(gateway); }
    @Override public Snapshot snapshot(String sessionId, String roomId) {
        StudyFixGateway.AssignmentCodeSnapshot s = gateway.assignmentCodeSnapshot(sessionId, roomId);
        return new Snapshot(s.roomId(), s.code(), s.version(), s.draftActive(), s.editTurnActive());
    }
    @Override public long loadStarterCode(String sessionId, String roomId, long expectedVersion,
                                          String starterCode, String assignmentTitle) {
        return gateway.loadAssignmentStarterCode(sessionId, roomId, expectedVersion, starterCode, assignmentTitle);
    }
}
