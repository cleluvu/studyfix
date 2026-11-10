package studyfix;

import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.drafts.Draft_6455;
import studyfix.assignment.protocol.AssignmentJson;
import javax.net.ssl.SSLContext;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** RFC6455 qua thư viện Java-WebSocket; token chỉ nằm trong frame AUTH, không ở URL/log. */
final class PresenceWebSocketServer extends WebSocketServer implements AutoCloseable {
    interface Access {
        Object authenticate(String token,InetSocketAddress peer)throws Exception;
        void heartbeat(Object session,InetSocketAddress peer)throws Exception;
        Map<String,Object> snapshot(Object session,boolean admin)throws Exception;
    }
    private static final class Client {
        final long opened=System.currentTimeMillis();Object session;boolean admin;String previous="";int blocked;
    }
    private final Access access;
    private final int httpPort;private final boolean tls;
    private final Set<String> allowedOrigins;
    private final ScheduledExecutorService publisher=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"studyfix-presence-push");t.setDaemon(true);return t;});
    private final CountDownLatch ready=new CountDownLatch(1);
    private volatile Exception startupError;
    PresenceWebSocketServer(InetSocketAddress address,int httpPort,SSLContext ssl,Access access){
        super(address,2,List.of(new Draft_6455(List.of(),8192)));
        this.httpPort=httpPort;this.tls=ssl!=null;this.access=access;
        this.allowedOrigins=new HashSet<>();
        for(String origin:System.getenv().getOrDefault("STUDYFIX_WS_ALLOWED_ORIGINS","").split(","))if(!origin.isBlank())allowedOrigins.add(origin.strip());
        if(ssl!=null)setWebSocketFactory(new DefaultSSLWebSocketServerFactory(ssl));
        setConnectionLostTimeout(30);setReuseAddr(true);setDaemon(true);
    }
    void startChecked()throws java.io.IOException{
        start();try{if(!ready.await(5,TimeUnit.SECONDS)||startupError!=null){close();throw new java.io.IOException("Không mở được WebSocket. Kiểm tra STUDYFIX_WS_PORT.");}}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException("WebSocket startup interrupted.");}
    }
    @Override public void onOpen(WebSocket conn,ClientHandshake handshake){
        if(getConnections().size()>128||!"/presence".equals(handshake.getResourceDescriptor())||!originAllowed(handshake)) {conn.close(1008,"Origin/path not allowed");return;}
        conn.setAttachment(new Client());
    }
    private boolean originAllowed(ClientHandshake handshake){
        String origin=handshake.getFieldValue("Origin");
        if(allowedOrigins.contains(origin))return true;
        try{
            URI o=URI.create(origin),host=URI.create((tls?"https":"http")+"://"+handshake.getFieldValue("Host"));
            int port=o.getPort()==-1?(tls?443:80):o.getPort();
            return o.getScheme().equals(tls?"https":"http")&&o.getHost()!=null&&o.getHost().equalsIgnoreCase(host.getHost())&&port==httpPort&&o.getRawUserInfo()==null;
        }catch(RuntimeException e){return false;}
    }
    @Override public void onMessage(WebSocket conn,String message){
        Client client=conn.getAttachment();if(client==null){conn.close(1008,"Not initialized");return;}
        try{
            Map<String,Object> body=AssignmentJson.object(message);String type=AssignmentJson.text(body,"type","");
            synchronized(client){
                if(client.session==null){
                    if(!type.equals("AUTH"))throw new SecurityException();
                    client.session=access.authenticate(AssignmentJson.text(body,"token",""),conn.getRemoteSocketAddress());
                    conn.send("{\"type\":\"AUTH_OK\"}");
                }else if(type.equals("HEARTBEAT")){
                    access.heartbeat(client.session,conn.getRemoteSocketAddress());conn.send("{\"type\":\"HEARTBEAT_OK\"}");
                }else if(type.equals("SUBSCRIBE")){
                    client.admin=AssignmentJson.text(body,"view","room").equals("admin");client.previous="";
                }else throw new IllegalArgumentException();
            }
        }catch(Exception e){conn.close(1008,"Session or message rejected");}
    }
    private void publish(){
        for(WebSocket conn:getConnections()){
            Client client=conn.getAttachment();if(client==null)continue;
            try{synchronized(client){
                if(client.session==null){if(System.currentTimeMillis()-client.opened>5000)conn.close(1008,"Authentication timeout");continue;}
                String message=AssignmentJson.stringify(access.snapshot(client.session,client.admin));
                if(conn.hasBufferedData()){if(++client.blocked>2)conn.close(1013,"Slow client");continue;}client.blocked=0;
                if(!message.equals(client.previous)){conn.send(message);client.previous=message;}
            }}catch(Exception e){conn.close(1008,"Session unavailable");}
        }
    }
    @Override public void onClose(WebSocket conn,int code,String reason,boolean remote){conn.setAttachment(null);}
    @Override public void onError(WebSocket conn,Exception ex){if(ready.getCount()>0){startupError=ex;ready.countDown();}else if(conn!=null)conn.close(1011,"Connection error");}
    @Override public void onStart(){ready.countDown();publisher.scheduleWithFixedDelay(this::publish,0,2,TimeUnit.SECONDS);}
    @Override public void close(){publisher.shutdownNow();try{stop(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
