package vn.studyfix.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Client defaults. Values may be overridden by -Dstudyfix.host / -Dstudyfix.port. */
public record ClientConfig(String defaultHost, int controlPort, int screenSharePort,
                           int connectTimeoutMillis, int loginTimeoutMillis) {
    public static ClientConfig load() {
        Properties p = new Properties();
        try (InputStream in = ClientConfig.class.getResourceAsStream("/studyfix-client.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) { }
        String host = System.getProperty("studyfix.host", p.getProperty("server.host", "127.0.0.1"));
        int port = intProperty("studyfix.port", p.getProperty("server.port", "5000"));
        int screenPort = intProperty("studyfix.screen.port", p.getProperty("server.screenPort", "5001"));
        return new ClientConfig(host, port, screenPort, 5000, 8000);
    }
    private static int intProperty(String systemKey, String fallback) {
        String value = System.getProperty(systemKey, fallback);
        int defaultValue;
        try { defaultValue = Integer.parseInt(fallback); } catch (NumberFormatException ignored) { defaultValue = 5000; }
        try { int n = Integer.parseInt(value); return n > 0 && n <= 65535 ? n : defaultValue; }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
