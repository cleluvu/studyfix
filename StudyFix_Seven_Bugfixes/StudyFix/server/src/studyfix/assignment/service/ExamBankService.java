package studyfix.assignment.service;

import studyfix.Identity;
import studyfix.assignment.persistence.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Authenticated bank API and bounded preview/commit import protocol. */
public final class ExamBankService {
 public static final int MAX_IMPORT_BYTES=2*1024*1024;
 public final ExamBankRepository repository;
 private record Pending(String owner,long expires,List<Map<String,Object>> plans){}
 private final Map<String,Pending> imports=new ConcurrentHashMap<>();
 public ExamBankService(AssignmentRepository repo)throws Exception{repository=new ExamBankRepository(repo);}
 private static void teacher(Identity who){if(who.role()!=Identity.Role.TEACHER)throw new SecurityException("Chỉ giảng viên được sử dụng ngân hàng đề thi.");}
 public Map<String,Object> handle(Map<String,Object> req,Identity who)throws Exception{
  teacher(who);String type=text(req,"type",""),id=text(req,"versionId","");int rev=integer(req,"expectedRevision",0);
  Object data=switch(type){
   case "EXAMBANK_LIST" -> repository.directory(who,req);
   case "EXAMBANK_SOURCES" -> repository.sources(who);
   case "EXAMBANK_GET" -> mapOf("exam",repository.get(who,id,false));
   case "EXAMBANK_PREVIEW" -> mapOf("exam",repository.get(who,id,true));
   case "EXAMBANK_SAVE" -> mapOf("exam",repository.save(who,req));
   case "EXAMBANK_APPROVE" -> mapOf("exam",repository.changeStatus(who,id,rev,true));
   case "EXAMBANK_ARCHIVE" -> mapOf("exam",repository.changeStatus(who,id,rev,false));

   case "EXAMBANK_EXPORT" -> export(who,list(req.get("versionIds")).stream().map(String::valueOf).toList(),text(req,"format","JSON"));
   case "EXAMBANK_IMPORT_COMMIT" -> commit(who,text(req,"previewId",""));
   default -> throw new IllegalArgumentException("Thao tác ngân hàng đề thi không hợp lệ.");
  };return mapOf("requestId",text(req,"requestId",""),"status","OK","data",data);
 }
 private Map<String,Object> export(Identity who,List<String> ids,String format)throws Exception{
  Map<String,Object> pack=repository.export(who,ids);byte[] bytes=stringify(pack).getBytes(StandardCharsets.UTF_8);if(!format.equals("JSON")&&!format.equals("ZIP"))throw new IllegalArgumentException("Chọn JSON hoặc ZIP.");if(format.equals("ZIP")){ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(bytes);zip.closeEntry();}bytes=out.toByteArray();}return mapOf("filename","studyfix-exam-bank."+format.toLowerCase(Locale.ROOT),"contentType",format.equals("ZIP")?"application/zip":"application/json","base64",Base64.getEncoder().encodeToString(bytes));
 }
 public Map<String,Object> previewImport(Identity who,byte[] bytes,boolean zipFile,boolean update)throws Exception{
  teacher(who);if(bytes.length==0||bytes.length>MAX_IMPORT_BYTES)throw new IllegalArgumentException("Gói đề cần có dữ liệu, tối đa 2 MiB.");byte[] content=bytes;
  if(zipFile){boolean found=false;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){ZipEntry entry=zip.getNextEntry();if(entry==null||!entry.getName().equals("manifest.json")||entry.isDirectory())throw new IllegalArgumentException("ZIP chỉ chứa manifest.json ở thư mục gốc.");content=zip.readNBytes(MAX_IMPORT_BYTES+1);if(content.length>MAX_IMPORT_BYTES)throw new IllegalArgumentException("Manifest giải nén vượt 2 MiB.");if(zip.getNextEntry()!=null)throw new IllegalArgumentException("Gói đề không nhận file đính kèm hoặc mã thực thi; chỉ có manifest.json.");found=true;}if(!found)throw new IllegalArgumentException("Thiếu manifest.json.");}
  // The core parser is recursive. Bound nesting before parsing untrusted imports.
  int depth=0;boolean string=false,escape=false;for(byte ch:content){if(string){if(escape)escape=false;else if(ch=='\\')escape=true;else if(ch=='"')string=false;}else if(ch=='"')string=true;else if(ch=='{'||ch=='['){if(++depth>30)throw new IllegalArgumentException("JSON lồng quá sâu.");}else if(ch=='}'||ch==']')depth--;}
  List<Map<String,Object>> plans=repository.prepareImport(who,object(new String(content,StandardCharsets.UTF_8)),update);long now=System.currentTimeMillis();imports.entrySet().removeIf(e->e.getValue().expires<now);if(imports.size()>=100)throw new IllegalStateException("Nhiều phiên xem trước đang mở. Thử lại sau.");String token=UUID.randomUUID().toString();imports.put(token,new Pending(who.userId(),now+5*60_000,plans));return mapOf("previewId",token,"expiresAt",now+5*60_000,"exams",plans.stream().map(p->mapOf("code",p.get("code"),"title",map(p.get("document")).get("title"),"questionCount",list(map(p.get("document")).get("items")).size(),"action",text(p,"bankId","").isBlank()?"CREATE":"UPDATE","status","DRAFT")).toList());
 }
 private synchronized Map<String,Object> commit(Identity who,String id)throws Exception{Pending p=imports.get(id);if(p==null||!p.owner.equals(who.userId())||p.expires<System.currentTimeMillis())throw new SecurityException("Bản xem trước hết hạn hoặc không thuộc bạn. Chọn lại file.");int count=repository.importAll(who,p.plans);imports.remove(id);return mapOf("imported",count);}
}
