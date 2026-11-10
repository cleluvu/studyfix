package studyfix.assignment.model;

/** Khai báo rõ loại hoạt động học tập, không đoán từ tên bài hay code mẫu. */
public enum ExerciseMode {
    DEBUG_COLLAB,
    BUILD_FROM_SCRATCH;

    public static ExerciseMode parse(String value) {
        if (value == null || value.isBlank()) return BUILD_FROM_SCRATCH;
        try { return ExerciseMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("mode phải là DEBUG_COLLAB hoặc BUILD_FROM_SCRATCH."); }
    }
}
