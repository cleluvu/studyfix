package vn.studyfix.client.service;

import vn.studyfix.client.config.ClientConfig;
import vn.studyfix.client.model.SessionInfo;
import vn.studyfix.client.net.TcpControlConnection;
import vn.studyfix.client.protocol.WireCommands;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthService {
    private static final Pattern LOGIN = Pattern.compile("^\\[LOGIN_OK] session=([^ ]+) userId=([^ ]+) role=([^ ]+) \\| ?(.*)$", Pattern.DOTALL);
    private final ClientConfig config;
    public AuthService(ClientConfig config) { this.config = config; }
    public LoginResult login(String host, int port, String username, String password) throws Exception {
        TcpControlConnection connection = TcpControlConnection.connect(host, port, config.connectTimeoutMillis());
        try {
            String response = connection.sendAndAwait(WireCommands.login(username, password),
                    s -> s.startsWith("[LOGIN_OK]") || s.startsWith("[LOGIN_FAILED]"), Duration.ofMillis(config.loginTimeoutMillis()));
            if (response.startsWith("[LOGIN_FAILED]")) throw new LoginException(response);
            Matcher matcher = LOGIN.matcher(response);
            if (!matcher.matches()) throw new IOException("Server trả LOGIN_OK sai định dạng: " + response);
            SessionInfo session = new SessionInfo(username, matcher.group(4).trim(), matcher.group(1),
                    matcher.group(2), matcher.group(3));
            return new LoginResult(connection, session);
        } catch (Exception e) {
            connection.close();
            throw e;
        }
    }
    public record LoginResult(TcpControlConnection connection, SessionInfo session) { }
    public static final class LoginException extends Exception { public LoginException(String message) { super(message); } }
}
