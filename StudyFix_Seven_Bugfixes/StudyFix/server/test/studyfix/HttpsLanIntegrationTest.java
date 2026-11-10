package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Trust only the generated test CA; do not install it into the operating system. */
public final class HttpsLanIntegrationTest {
    public static void main(String[] args)throws Exception{
        System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
        Path ca=Path.of(args[0]);KeyStore trust=KeyStore.getInstance("PKCS12");trust.load(null,null);
        try(var in=Files.newInputStream(ca)){trust.setCertificateEntry("test-ca",CertificateFactory.getInstance("X.509").generateCertificate(in));}
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,factory.getTrustManagers(),null);
        HttpClient client=HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(5)).build();
        Path data=Files.createTempDirectory(Path.of("build"),"https-lan-test-");
        try(Server core=new Server(0,data.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),data.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
            core.start();assignments.start();web.start();String base="https://127.0.0.1:"+web.port();
            HttpResponse<String> health=client.send(HttpRequest.newBuilder(URI.create(base+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(health.statusCode()!=200||!Boolean.TRUE.equals(map(object(health.body()).get("webSocket")).get("tls")))throw new AssertionError("HTTPS/WSS health failed");
            HttpResponse<String> login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(stringify(mapOf("username","teacher","password","Teacher123!")))).build(),HttpResponse.BodyHandlers.ofString());
            if(login.statusCode()!=200)throw new AssertionError("HTTPS login failed");
            String token=text(object(login.body()),"token","");
            WebSocketPresenceIntegrationTest.Listener listener=new WebSocketPresenceIntegrationTest.Listener();
            WebSocket socket=client.newWebSocketBuilder().header("Origin",base).buildAsync(URI.create("wss://127.0.0.1:"+web.webSocketPort()+"/presence"),listener).get(5,TimeUnit.SECONDS);
            socket.sendText(stringify(mapOf("type","AUTH","token",token)),true).join();listener.next("AUTH_OK");listener.next("PRESENCE");socket.sendClose(1000,"done").join();
            System.out.println("PASS HTTPS CA trust + SAN validation, HTTPS login, WSS authentication and presence on actual StudyFix server. No OS trust store or production database modified.");
        }
    }
}
