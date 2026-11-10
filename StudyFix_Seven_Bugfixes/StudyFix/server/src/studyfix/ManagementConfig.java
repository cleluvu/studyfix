package studyfix;

import java.nio.file.Path;

/** SQLite is the only supported database engine. */
final class ManagementConfig {
    final String url;
    ManagementConfig(String url) {
        if (url == null || !url.startsWith("jdbc:sqlite:") || url.substring(12).isBlank())
            throw new IllegalArgumentException("Chỉ hỗ trợ SQLite với đường dẫn database hợp lệ.");
        this.url = url;
    }
    static ManagementConfig environment() { return environment(Path.of("data/studyfix.db")); }
    static ManagementConfig environment(Path core) {
        String mode = System.getProperty("studyfix.management.db", System.getenv().getOrDefault("STUDYFIX_MANAGEMENT_DB", "sqlite"));
        if (!mode.equalsIgnoreCase("sqlite")) throw new IllegalArgumentException("STUDYFIX_MANAGEMENT_DB chỉ hỗ trợ sqlite.");
        // Isolated core-only SQLite tests exercise the original classroom repository.
        if (Boolean.getBoolean("studyfix.legacy.sqlite")) return null;
        Path absolute = core.toAbsolutePath().normalize();
        String filename = absolute.getFileName().toString();
        Path fallback = absolute.resolveSibling(filename.equals("studyfix.db") ? "studyfix-management.db" : filename + ".management.db");
        String location = System.getProperty("studyfix.management.sqlite", System.getenv().getOrDefault("STUDYFIX_SQLITE_MANAGEMENT_PATH", fallback.toString()));
        return new ManagementConfig("jdbc:sqlite:" + Path.of(location).toAbsolutePath().normalize());
    }
    @Override public String toString() { return "ManagementConfig[sqlite]"; }
}
