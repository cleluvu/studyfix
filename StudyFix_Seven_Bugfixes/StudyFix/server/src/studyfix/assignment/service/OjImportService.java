package studyfix.assignment.service;
import studyfix.Identity;
import studyfix.assignment.catalog.OjPackage;
import studyfix.assignment.persistence.OjRepository;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Short-lived, owner-scoped import previews. Only committed definitions enter the database. */
public final class OjImportService {
 private record Preview(String owner,OjPackage pack,long expires){}
 private final Map<String,Preview> pending=new ConcurrentHashMap<>();private final OjRepository repo;
 public OjImportService(OjRepository repo){this.repo=repo;}
 private static void teacher(Identity who){if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Teacher only");}
 public synchronized Map<String,Object> preview(Identity who,byte[] bytes)throws Exception{teacher(who);pending.values().removeIf(p->p.expires()<System.currentTimeMillis()||p.owner().equals(who.userId()));if(pending.size()>=4)throw new IllegalStateException("Import đang bận, thử lại sau.");var pack=new OjPackage(bytes);String ticket=UUID.randomUUID().toString();pending.put(ticket,new Preview(who.userId(),pack,System.currentTimeMillis()+300000));return mapOf("previewId",ticket,"report",pack.preview(),"expiresInSeconds",300);}
 public synchronized Map<String,Object> commit(Identity who,String ticket,String hash)throws Exception{return commit(who,ticket,hash,"PRACTICE");}
 public synchronized Map<String,Object> commit(Identity who,String ticket,String hash,String bankScope)throws Exception{teacher(who);studyfix.assignment.persistence.QuestionBankRepository.scope(bankScope);Preview preview=pending.get(ticket);if(preview==null||preview.expires()<System.currentTimeMillis()||!preview.owner().equals(who.userId())||!preview.pack().sha256.equals(hash))throw new SecurityException("Preview không hợp lệ hoặc đã hết hạn; chọn file lại.");var result=repo.importPackage(preview.pack(),who,false,bankScope);pending.remove(ticket);return result;}
}
