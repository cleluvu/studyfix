package studyfix;
import static studyfix.FullAudit.*;
import java.nio.file.*;
/** Reads an already migrated legacy fixture over the real server protocol. */
public final class LegacyHistorySmoke {
    public static void main(String[] args)throws Exception {
        Path db=Path.of(args[0]);
        try(Server server=new Server(0,db)) {
            server.start();
            try(Peer student=new Peer(server,"student1","Student123!")) {
                student.cmd("/join 9","--- CODE CHUNG ---");
                String listing=student.cmd("/history","[Lịch sử phòng");require(listing.contains("old-user")&&listing.contains("Tên lúc sửa"),"Legacy author reassigned");
                String detail=student.cmd("/history 1","CODE SAU KHI SỬA");require(detail.endsWith("b")&&detail.contains("Tên lúc sửa"),"Legacy snapshot unreadable");
                var history=server.rooms.get("9").snapshot().history();
                require(history.get(1).getEditorId().equals("old-user")&&history.get(1).getCreatedAt().toString().equals("2020-01-02T00:00:00Z"),"Legacy metadata changed");
                System.out.println("PASS F10 real TCP /history reads legacy author, exact snapshot and original timestamp; db="+db);
            }
        }finally{HB.shutdownNow();}
    }
}
