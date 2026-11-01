package studyfix;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import static studyfix.assignment.protocol.AssignmentJson.mapOf;

/** Bounded WebRTC signaling. Media flows between opted-in members of the same room. */
final class MeetingCalls {
    record Peer(String id,String room,String name,String role,boolean audio,boolean video) { }
    record Signal(long id,String room,String from,String kind,String payload) { }
    private final Map<String,Peer> peers=new HashMap<>();
    private final Map<String,Deque<Signal>> inbox=new HashMap<>();
    private final Map<String,Deque<Long>> rates=new HashMap<>();
    private final Map<String,Long> seen=new HashMap<>();
    private final AtomicLong sequence=new AtomicLong();
    static final int CAPACITY=16;
    synchronized void join(String id,String room,Identity who,boolean audio,boolean video) {
        Peer previous=peers.get(id);
        if(previous!=null&&!previous.room().equals(room))leave(id);
        if(!peers.containsKey(id)&&peers.values().stream().filter(p->p.room().equals(room)).count()>=CAPACITY)
            throw new IllegalStateException("Phòng gọi đã đủ 16 thiết bị. Bạn vẫn có thể học code và chat.");
        peers.put(id,new Peer(id,room,who.displayName(),who.role().name(),audio,video));
        seen.put(id,System.currentTimeMillis());
        inbox.computeIfAbsent(id,k->new ArrayDeque<>());
    }
    synchronized void state(String id,String room,boolean audio,boolean video) {
        Peer p=member(id,room);peers.put(id,new Peer(id,room,p.name(),p.role(),audio,video));
    }
    synchronized void signal(String id,String room,String to,String kind,String payload) {
        member(id,room);member(to,room);
        if(id.equals(to))throw new IllegalArgumentException("Không gửi tín hiệu cho chính mình.");
        if(!Set.of("OFFER","ANSWER","ICE").contains(kind))throw new IllegalArgumentException("Tín hiệu cuộc gọi không hợp lệ.");
        if(payload==null||payload.isBlank()||payload.length()>(kind.equals("ICE")?4096:64000))throw new IllegalArgumentException("Tín hiệu rỗng hoặc vượt giới hạn.");
        long now=System.currentTimeMillis();Deque<Long> history=rates.computeIfAbsent(id,k->new ArrayDeque<>());
        while(!history.isEmpty()&&history.peekFirst()<now-60000)history.removeFirst();
        if(history.size()>=600)throw new IllegalStateException("Quá nhiều tín hiệu cuộc gọi. Vui lòng thử lại sau.");
        Deque<Signal> queue=inbox.get(to);
        if(queue.size()>=512||queue.stream().mapToInt(s->s.payload().length()).sum()+payload.length()>256000)throw new IllegalStateException("Hàng đợi nhận cuộc gọi đầy. Người nhận cần kết nối lại cuộc gọi.");
        history.addLast(now);queue.addLast(new Signal(sequence.incrementAndGet(),room,id,kind,payload));
    }
    synchronized Map<String,Object> poll(String id,String room,long after) {
        if(peers.containsKey(id)&&peers.get(id).room().equals(room))seen.put(id,System.currentTimeMillis());
        Deque<Signal> queue=inbox.get(id);List<Map<String,Object>> signals=new ArrayList<>();
        if(queue!=null) {
            while(!queue.isEmpty()&&queue.peekFirst().id()<=after)queue.removeFirst();
            for(Signal s:queue)if(s.room().equals(room))signals.add(mapOf("id",s.id(),"fromPeerId",s.from(),"kind",s.kind(),"payload",s.payload()));
        }
        List<Map<String,Object>> roster=peers.values().stream().filter(p->p.room().equals(room)).sorted(Comparator.comparing(Peer::id))
                .map(p->mapOf("peerId",p.id(),"displayName",p.name(),"role",p.role(),"audio",p.audio(),"video",p.video())).toList();
        return mapOf("ok",true,"peers",roster,"signals",signals,"capacity",CAPACITY);
    }
    private Peer member(String id,String room) {
        Peer p=peers.get(id);
        if(p==null||!p.room().equals(room))throw new SecurityException("Thiết bị chưa tham gia cuộc gọi trong phòng này.");
        return p;
    }
    synchronized void leave(String id) {
        peers.remove(id);inbox.remove(id);rates.remove(id);
        seen.remove(id);
        for(Deque<Signal> queue:inbox.values())queue.removeIf(s->s.from().equals(id));
    }
    synchronized void prune(BiPredicate<String,String> active) {
        for(Peer p:List.copyOf(peers.values()))if(!active.test(p.id(),p.room())||System.currentTimeMillis()-seen.getOrDefault(p.id(),0L)>20000)leave(p.id());
    }
}
