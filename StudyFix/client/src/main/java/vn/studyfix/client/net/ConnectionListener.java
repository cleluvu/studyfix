package vn.studyfix.client.net;

public interface ConnectionListener {
    default void onMessage(String message) { }
    default void onDisconnected(Throwable cause) { }
}
