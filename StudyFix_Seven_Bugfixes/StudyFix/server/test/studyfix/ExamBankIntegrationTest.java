package studyfix;

import studyfix.assignment.persistence.AssignmentRepository;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP, SQLite, auth and restart tests; all fixtures isolated under build/. */
public final class ExamBankIntegrationTest {
 static Map<String,Object> doc(List<Object> items){return mapOf("title","Đề kiểm tra Mảng","description","Two immutable questions","durationMinutes",90,"totalPoints",100,"items",items,"sourceWeeks",List.of(),"grading",mapOf("inherit",true,"showScore",false,"showPublicTests",false,"allowResubmit",false,"allowTemplate",false));}
 static Map<String,Object> item(String id,int points){return mapOf("exerciseId",id,"points",points);}
 static Map<String,Object> save(String base,String t,String code,Map<String,Object> doc)throws Exception{return obj(assign(base,t,"EXAMBANK_SAVE",mapOf("code",code,"document",doc)).get("exam"));}
 static Map<String,Object> get(String base,String t,String id)throws Exception{return obj(assign(base,t,"EXAMBANK_GET",mapOf("versionId",id)).get("exam"));}
 static Map<String,Object> approve(String base,String t,Map<String,Object> exam)throws Exception{return obj(assign(base,t,"EXAMBANK_APPROVE",mapOf("versionId",exam.get("id"),"expectedRevision",exam.get("revision"))).get("exam"));}
 static Response preview(String base,String token,byte[] bytes,boolean zip,boolean update)throws Exception{
  var request=HttpRequest.newBuilder(URI.create(base+"/api/exam-bank/import?format="+(zip?"ZIP":"JSON")+"&update="+update)).header("Authorization","Bearer "+token).header("Content-Type","application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();var r=HTTP.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));return new Response(r.statusCode(),object(r.body()));
 }
 static Map<String,Object> exported(String base,String t,String id)throws Exception{var data=assign(base,t,"EXAMBANK_EXPORT",mapOf("versionIds",List.of(id),"format","JSON"));return object(new String(Base64.getDecoder().decode(text(data,"base64","")),StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.legacy.sqlite","true");
  System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");System.setProperty("studyfix.web.root",Path.of("../web-client/static").toAbsolutePath().toString());
  Path tmp=Files.createTempDirectory(Path.of("build"),"exam-bank-integration-"),db=tmp.resolve("core.db"),learning=tmp.resolve("learning.db");String approvedId,contestId;
  try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,32,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),t=login(base,"teacher","Teacher123!"),s=login(base,"student1","Student123!");
   try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role) VALUES(?,?,?,?,?)")){p.setString(1,UUID.randomUUID().toString());p.setString(2,"exam_teacher2");p.setString(3,Passwords.hash("Teacher234!".toCharArray()));p.setString(4,"Other Teacher");p.setString(5,"TEACHER");p.executeUpdate();}String foreign=login(base,"exam_teacher2","Teacher234!");
   check(send(base,s,"EXAMBANK_LIST",Map.of()).status()!=200,"Student cannot manage bank");
   check(number(assign(base,t,"EXAMBANK_LIST",Map.of()),"total",-1)==0,"bank starts empty, not seeded with demos");
   assign(base,t,"CATALOG_CREATE",QuestionBankIntegrationTest.question("EB_A","First frozen question","PUBLISHED"));assign(base,t,"CATALOG_CREATE",QuestionBankIntegrationTest.question("EB_B","Second frozen question","PUBLISHED"));
   Map<String,Object> d=doc(List.of(item("EB_A",40),item("EB_B",60))),draft=save(base,t,"EXAM_A",d);String id=text(draft,"id","");
   check(!id.isBlank()&&draft.get("status").equals("DRAFT"),"save without a classroom");
   check(send(base,foreign,"EXAMBANK_GET",mapOf("versionId",id)).status()!=200,"foreign Teacher cannot inspect definition");
   for(String type:List.of("EXAMBANK_GET","EXAMBANK_PREVIEW","EXAMBANK_SAVE","EXAMBANK_APPROVE","EXAMBANK_ARCHIVE","EXAMBANK_CLONE","EXAMBANK_EXPORT","EXAMBANK_SOURCES"))check(send(base,s,type,mapOf("versionId",id)).status()!=200,"Student denied "+type);
   check(send(base,t,"EXAMBANK_SAVE",mapOf("code","EXAM_A","document",d)).status()!=200,"duplicate exam code rejected");
   check(send(base,t,"EXAMBANK_SAVE",mapOf("code","DUPLICATE_QUESTION","document",doc(List.of(item("EB_A",50),item("EB_A",50))))).status()!=200,"duplicate questions rejected");
   Map<String,Object> bad=save(base,t,"BAD_SUM",doc(List.of(item("EB_A",30))));check(send(base,t,"EXAMBANK_APPROVE",mapOf("versionId",bad.get("id"),"expectedRevision",bad.get("revision"))).status()!=200,"invalid total cannot be approved");
   Map<String,Object> saved=obj(assign(base,t,"EXAMBANK_SAVE",mapOf("code","EXAM_A","versionId",id,"expectedRevision",draft.get("revision"),"document",d)).get("exam"));
   check(send(base,t,"EXAMBANK_SAVE",mapOf("code","EXAM_A","versionId",id,"expectedRevision",draft.get("revision"),"document",d)).status()!=200,"optimistic concurrency rejects stale draft");
   Map<String,Object> approved=approve(base,t,saved);approvedId=id;check(approved.get("status").equals("APPROVED"),"approve valid exam");
   check(send(base,t,"EXAMBANK_SAVE",mapOf("code","EXAM_A","versionId",id,"expectedRevision",approved.get("revision"),"document",d)).status()!=200,"approved definition immutable");
   String pub=stringify(assign(base,t,"EXAMBANK_PREVIEW",mapOf("versionId",id)));check(!pub.contains("SECRET_TEST")&&!pub.contains("referenceSolution")&&!pub.contains("grading")&&pub.contains("OLD_PUBLIC"),"preview allowlist excludes hidden tests and judge config");
   Map<String,Object> clone=obj(assign(base,t,"EXAMBANK_CLONE",mapOf("versionId",id,"expectedRevision",approved.get("revision"))).get("exam"));check(number(clone,"version",0)==2&&clone.get("status").equals("DRAFT"),"new version creates editable draft");
   check(number(assign(base,t,"EXAMBANK_LIST",mapOf("allVersions",true,"query","EXAM_A")),"total",0)==2,"version directory returns both versions");
   check(number(assign(base,t,"EXAMBANK_LIST",mapOf("query","de kiem tra mang")),"total",0)>0,"accent insensitive search");
   check(number(assign(base,foreign,"EXAMBANK_LIST",Map.of()),"total",-1)==0,"scoped directory excludes foreign definitions");
   Map<String,Object> pack=exported(base,t,id);check(!stringify(pack).contains("SECRET_TEST")&&!stringify(pack).contains("OLD_PUBLIC"),"export contains references, no judge data");
   Map<String,Object> def=map(list(pack.get("exams")).getFirst());def.put("code","IMPORTED_EXAM");byte[] bytes=stringify(pack).getBytes(StandardCharsets.UTF_8);Response pre=preview(base,t,bytes,false,false);check(pre.status()==200,"JSON import preview succeeds");String preview=text(obj(pre.body().get("data")),"previewId","");
   check(send(base,foreign,"EXAMBANK_IMPORT_COMMIT",mapOf("previewId",preview)).status()!=200,"preview bound to owner");check(preview(base,s,bytes,false,false).status()==403,"Student cannot upload exam package");
   check(number(assign(base,t,"EXAMBANK_IMPORT_COMMIT",mapOf("previewId",preview)),"imported",0)==1,"commit dependency-checked draft");check(send(base,t,"EXAMBANK_IMPORT_COMMIT",mapOf("previewId",preview)).status()!=200,"single use import receipt");
   check(preview(base,t,bytes,false,false).status()!=200,"duplicate code import requires explicit new version");check(preview(base,t,bytes,false,true).status()==200,"explicit duplicate version preview");
   Map<String,Object> mismatch=object(stringify(pack));
   Map<String,Object> wrongItem=map(list(map(map(list(mismatch.get("exams")).getFirst()).get("document")).get("items")).getFirst());wrongItem.put("sha256","0".repeat(64));check(preview(base,t,stringify(mismatch).getBytes(StandardCharsets.UTF_8),false,true).status()!=200,"mismatched question fingerprint rejected");
   var zipExport=assign(base,t,"EXAMBANK_EXPORT",mapOf("versionIds",List.of(id),"format","ZIP"));check(preview(base,t,Base64.getDecoder().decode(text(zipExport,"base64","")),true,true).status()==200,"ZIP export imports correctly");
   ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("../manifest.json"));zip.write(bytes);zip.closeEntry();}check(preview(base,t,out.toByteArray(),true,true).status()!=200,"unsafe ZIP path rejected");
   // Tampering with a published question creates another snapshot. Previously approved exam keeps the original.
   Map<String,Object> update=QuestionBankIntegrationTest.question("EB_A","Changed future question","PUBLISHED");update.put("expectedRevision",1);assign(base,t,"BANK_UPDATE",update);check(stringify(get(base,t,id)).contains("First frozen question"),"question updates do not rewrite approved exam");
   check(preview(base,t,bytes,false,true).status()==200,"backup resolves published immutable question after a newer question revision exists");
   Map<String,Object> atomic=exported(base,t,id),fresh=object(stringify(map(list(atomic.get("exams")).getFirst()))),existing=object(stringify(fresh));fresh.put("code","ATOMIC_FRESH");existing.put("code","IMPORTED_EXAM");atomic.put("exams",List.of(fresh,existing));
   Response atomicPreview=preview(base,t,stringify(atomic).getBytes(StandardCharsets.UTF_8),false,true);check(atomicPreview.status()==200,"multi-exam import previews without writes");String atomicToken=text(obj(atomicPreview.body().get("data")),"previewId","");
   Map<String,Object> imported=rows(assign(base,t,"EXAMBANK_LIST",mapOf("query","IMPORTED_EXAM")).get("exams")).getFirst();assign(base,t,"EXAMBANK_CLONE",mapOf("versionId",imported.get("id"),"expectedRevision",imported.get("revision")));
   check(send(base,t,"EXAMBANK_IMPORT_COMMIT",mapOf("previewId",atomicToken)).status()!=200,"changed bank invalidates import plan");check(number(assign(base,t,"EXAMBANK_LIST",mapOf("query","ATOMIC_FRESH")),"total",-1)==0,"late failure rolls back earlier new exam in same import transaction");
   call(base,"/api/rooms/current/command",t,mapOf("action","CREATE_ROOM","roomCode","EXAM_BANK_TEST"));join(base,s,"EXAM_BANK_TEST");
   List<Object> manyItems=new ArrayList<>();for(int n=0;n<21;n++){String qid="EB_BULK_"+n;assign(base,t,"CATALOG_CREATE",QuestionBankIntegrationTest.question(qid,"Bulk fixture "+n,"PUBLISHED"));manyItems.add(item(qid,1));}Map<String,Object> manyDoc=doc(manyItems);manyDoc.put("totalPoints",21);var many=approve(base,t,save(base,t,"OVER_20",manyDoc));String manyContest=text(assign(base,t,"CONTEST_CREATE",mapOf("title","More than twenty","durationMinutes",10,"examVersionId",many.get("id"))),"contestId","");check(rows(assign(base,s,"CONTEST_PROBLEMS_GET",mapOf("contestId",manyContest)).get("problems")).size()==21,"exam-bank contest retains all questions beyond old direct-creator limit");
   contestId=text(assign(base,t,"CONTEST_CREATE",mapOf("title","From bank","durationMinutes",15,"examVersionId",id)),"contestId","");
   var problems=rows(assign(base,s,"CONTEST_PROBLEMS_GET",mapOf("contestId",contestId)).get("problems"));check(problems.size()==2&&number(problems.getFirst(),"maxScore",0)==40,"contest preserves frozen question order and points");check(text(problems.getFirst(),"template","x").isEmpty(),"template policy enforced");
   String submission=text(obj(assign(base,s,"CONTEST_SUBMIT",mapOf("contestId",contestId,"exerciseId","EB_A","sourceCode","public class Main {}","idempotencyKey",UUID.randomUUID().toString())).get("submission")),"id","");
   AssignmentRepository repo=new AssignmentRepository(learning);check(repo.getContestSubmission(submission).maxScore()==40,"submission maximum is exam item score, not 100");
   check(send(base,s,"CONTEST_SUBMIT",mapOf("contestId",contestId,"exerciseId","EB_A","sourceCode","public class Main {}","idempotencyKey",UUID.randomUUID().toString())).status()!=200,"no-resubmit policy enforced on server");
   Map<String,Object> result=obj(assign(base,s,"CONTEST_SUBMISSION_RESULT",mapOf("submissionId",submission)).get("submission"));check(result.get("score")==null&&Boolean.TRUE.equals(result.get("scoreHidden")),"score withheld before contest ends");check(list(result.get("testResults")).isEmpty()&&result.get("passedTests")==null,"test result details withheld by policy");
   assign(base,t,"EXAMBANK_ARCHIVE",mapOf("versionId",id,"expectedRevision",approved.get("revision")));check(send(base,t,"CONTEST_CREATE",mapOf("title","Archived","durationMinutes",10,"examVersionId",id)).status()!=200,"archived exam cannot start new contest");check(rows(assign(base,s,"CONTEST_PROBLEMS_GET",mapOf("contestId",contestId)).get("problems")).size()==2,"archive leaves existing contest intact");
   if(args.length>0&&args[0].equals("--browser")){Files.writeString(tmp.resolve("browser.json"),stringify(mapOf("base",base,"directory",tmp.toAbsolutePath().toString())));System.out.println("BROWSER_FIXTURE "+tmp.resolve("browser.json").toAbsolutePath());Thread.sleep(20*60_000);}
  }
  try(Server core=new Server(0,db);AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),learning,0,32,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)){core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),t=login(base,"teacher","Teacher123!");check(get(base,t,approvedId).get("status").equals("ARCHIVED"),"versions and state survive restart");}
  System.out.println("ExamBankIntegrationTest PASS: "+checks+" assertions, real HTTP/SQLite, permissions, immutable snapshots, scoring, import/export, policies and restart.");
 }
}
