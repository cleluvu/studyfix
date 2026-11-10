package studyfix;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// Bản chụp không thay đổi sau khi tạo; không giữ tham chiếu đến kết nối client.
public final class CodeRevision {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss.SSS XXX")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final long version;
    private final String editorId;
    private final String editorName;
    private final Instant createdAt;
    private final String sourceCode;
    private final Long previousVersion;
    private final String changes;

    public CodeRevision(long version, String editorId,
                        String editorName, String sourceCode) {
        this(version, editorId, editorName, sourceCode, Instant.now());
    }

    public CodeRevision(long version, String editorId, String editorName,
                        String sourceCode, Instant createdAt) {
        this(version, editorId, editorName, sourceCode, createdAt,
                version == 0 ? null : version - 1, version == 0 ? CodeDiff.INITIAL : CodeDiff.MISSING);
    }

    public CodeRevision(long version, String editorId, String editorName,
                        String sourceCode, Instant createdAt, Long previousVersion, String changes) {
        this.previousVersion = previousVersion;
        this.changes = java.util.Objects.requireNonNull(changes);
        this.version = version;
        this.editorId = editorId;
        this.editorName = editorName;
        this.sourceCode = sourceCode;
        this.createdAt = createdAt;
    }

    public long getVersion() { return version; }
    public String getEditorId() { return editorId; }
    public String getEditorName() { return editorName; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSourceCode() { return sourceCode; }

    public Long getPreviousVersion() { return previousVersion; }
    public String getChanges() { return changes; }
    public String detail() {
        return (version == 0 ? "Phiên bản 0 — Code khởi tạo"
                : "Phiên bản " + (previousVersion == null ? "không xác định" : previousVersion)
                        + " → " + version)
                + "\nNgười sửa: " + editorName
                + "\nThời gian: " + FORMAT.format(createdAt)
                + "\n\nDÒNG ĐÃ THAY ĐỔI\n" + CodeDiff.display(changes)
                + "\n\nCODE SAU KHI SỬA (PHIÊN BẢN " + version + ")\n" + sourceCode;
    }

    public String summary() {
        return "Phiên bản " + version + " | Người sửa: " + editorName
                + " | userId (SYSTEM/legacy nếu có): " + editorId
                + " | Thời gian: " + FORMAT.format(createdAt);
    }
}
