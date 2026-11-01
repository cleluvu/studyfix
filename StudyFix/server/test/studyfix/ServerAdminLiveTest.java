package studyfix;

import java.net.ServerSocket;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.swing.*;

/** Actual Swing window, live TCP updates, selection, stop button and disposal. */
public final class ServerAdminLiveTest {
    static Object field(Object o,String name) throws Exception {
        var f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static Object[] admin={null};
    static Object edtField(String name) throws Exception {
        Object[] result={null};
        SwingUtilities.invokeAndWait(()->{try{result[0]=field(admin[0],name);}catch(Exception e){throw new RuntimeException(e);}});
        return result[0];
    }
    public static void main(String[] args) throws Exception {
        if(java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("BLOCKED actual Swing window: headless environment"); System.exit(2);
        }
        Path db=Files.createTempDirectory(Path.of("build"),"admin-live-").resolve("studyfix.db").toAbsolutePath();
        System.out.println("TEST_DATABASE="+db);
        System.setProperty("studyfix.port","0"); System.setProperty("studyfix.screen.port","0"); System.setProperty("studyfix.assignment.port","0"); System.setProperty("studyfix.web.port","0"); System.setProperty("studyfix.db",db.toString());
        SwingUtilities.invokeAndWait(()->{try{var c=ServerAdmin.class.getDeclaredConstructor();c.setAccessible(true);admin[0]=c.newInstance();}catch(Exception e){throw new RuntimeException(e);}});
        Server server=null;
        try {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while(server==null && System.nanoTime()<until){server=(Server)edtField("server");if(server==null)Thread.sleep(20);}
            if(server==null)throw new AssertionError("Admin startup failed");
            Server active=server; int port=server.port();
            try(PersonOneTest.Peer p=new PersonOneTest.Peer(server,"teacher","Teacher123!")) {
                String created=p.command("/create","Đã tạo và tham gia phòng:");String id=created.substring(created.lastIndexOf(':')+1).trim();
                long t=PersonOneTest.request(p,server,id);
                p.command("/update 0 "+t+" Tiếng Việt 😀\r\ncode mới","ĐÃ CẬP NHẬT");
                until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5); boolean[] ready={false};
                while(!ready[0] && System.nanoTime()<until) {
                    SwingUtilities.invokeAndWait(()->{try{
                        JList<?> history=(JList<?>)field(admin[0],"history");
                        ready[0]=history.getModel().getSize()==2;
                    }catch(Exception e){throw new RuntimeException(e);}});
                    if(!ready[0])Thread.sleep(20);
                }
                if(!ready[0])throw new AssertionError("UI did not refresh live revisions");
                System.out.println("PASS actual ServerAdmin refreshes new room/version/history from live TCP activity");
                SwingUtilities.invokeAndWait(()->{try{
                    JList<?> history=(JList<?>)field(admin[0],"history");history.setSelectedIndex(1);
                    JTextArea code=(JTextArea)field(admin[0],"code");
                    if(code.isEditable() || !code.getText().endsWith("Tiếng Việt 😀\r\ncode mới"))throw new AssertionError("Wrong selected revision");
                    history.setSelectedIndex(0);
                    if(!code.getText().contains(CodeDiff.INITIAL))throw new AssertionError("Wrong initial revision");
                    ((JButton)field(admin[0],"stop")).doClick();
                }catch(Exception e){throw new RuntimeException(e);}});
                System.out.println("PASS actual ServerAdmin revision selection remains read-only and preserves Unicode/newlines");
                PersonOneTest.eventually(()->!active.isRunning());
                PersonOneTest.eventually(()->active.clients.isEmpty());
                try(ServerSocket probe=new ServerSocket(port)) {System.out.println("PASS actual ServerAdmin stop button closes server sockets and frees port");}
            }
        } finally {
            SwingUtilities.invokeAndWait(()->{try{
                var stop=ServerAdmin.class.getDeclaredMethod("shutdown",boolean.class);stop.setAccessible(true);stop.invoke(admin[0],true);
                ((JFrame)field(admin[0],"frame")).dispose();
            }catch(Exception e){throw new RuntimeException(e);}});
            if(server!=null)server.close();
            ((ScheduledExecutorService)edtField("background")).shutdownNow();
        }
    }
}
