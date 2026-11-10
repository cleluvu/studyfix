package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

public final class ProblemBuilderIntegrationTest {
 static java.net.http.HttpResponse<byte[]> binary(String base,String token,String path,byte[] data)throws Exception{
  var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).header("Authorization","Bearer "+token);
  if(data!=null)request.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(data));else request.GET();
  return java.net.http.HttpClient.newHttpClient().send(request.build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
 }
 static byte[] zip(String... entries)throws Exception{var bytes=new java.io.ByteArrayOutputStream();try(var archive=new java.util.zip.ZipOutputStream(bytes)){for(int i=0;i<entries.length;i+=2){archive.putNextEntry(new java.util.zip.ZipEntry(entries[i]));archive.write(entries[i+1].getBytes(java.nio.charset.StandardCharsets.UTF_8));archive.closeEntry();}}return bytes.toByteArray();}
 static boolean rejectsZip(byte[] bytes){try{studyfix.assignment.persistence.ProblemTemplate.unzip(bytes);return false;}catch(studyfix.assignment.persistence.ProblemBuilderRepository.Failure expected){return true;}}
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"builder-test-"),db=temp.resolve("core.db"),learning=temp.resolve("learning.db");String saved;boolean docker="1".equals(System.getenv("STUDYFIX_BUILDER_DOCKER_TEST"));
  try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,docker?"eclipse-temurin:21-jdk":"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
   check(send(base,student,"BUILDER_LIST",Map.of()).status()!=200,"Student cannot list author drafts");
   Map<String,Object> create=mapOf("requestKey","create-draft-0001","payload",mapOf("title","Incomplete draft","javaStructure","PROGRAM","visibility","PRIVATE"));
   Map<String,Object> draft=obj(assign(base,teacher,"BUILDER_CREATE",create).get("draft"));saved=text(draft,"id","");
   check(number(draft,"revision",0)==1,"partial draft creates without statement/tests");
   check(saved.equals(obj(assign(base,teacher,"BUILDER_CREATE",create).get("draft")).get("id")),"network retry cannot duplicate draft");
   check(send(base,teacher,"BUILDER_CREATE",mapOf("requestKey","create-draft-0001","payload",mapOf("title","Different"))).status()!=200,"creation key cannot be reused for different content");
   check(send(base,student,"BUILDER_GET",mapOf("draftId",saved)).status()!=200,"Student cannot retrieve draft payload");
   check(send(base,teacher,"BUILDER_ADVANCE",mapOf("draftId",saved,"expectedRevision",1,"targetStep",2)).status()!=200,"cannot advance incomplete metadata");
   String code=text(assign(base,teacher,"BUILDER_SUGGEST_CODE",Map.of()),"code","");
   Map<String,Object> payload=mapOf("title","Updated draft","code",code,"javaStructure","PROGRAM","visibility","PRIVATE","weekIds",List.of(),"statement",mapOf("description","Unfinished statement"),"reference","PRIVATE_REFERENCE");
   Map<String,Object> save=mapOf("draftId",saved,"expectedRevision",1,"currentStep",1,"payload",payload);
   Map<String,Object> updated=obj(assign(base,teacher,"BUILDER_SAVE",save).get("draft"));check(number(updated,"revision",0)==2&&number(updated,"currentStep",0)==1,"save stores step and optimistic revision");
   check(number(obj(assign(base,teacher,"BUILDER_SAVE",save).get("draft")),"revision",0)==2,"identical uncertain save retry is idempotent");
   check(rows(assign(base,teacher,"BUILDER_HISTORY",mapOf("draftId",saved)).get("history")).size()==2,"retry does not duplicate history");
   check(!stringify(assign(base,teacher,"BUILDER_LIST",Map.of())).contains("PRIVATE_REFERENCE"),"draft directory excludes source/tests/reference payload");
   payload.put("title","Stale change");check(send(base,teacher,"BUILDER_SAVE",save).status()!=200,"stale editor cannot overwrite content");payload.put("title","Updated draft");
   check(send(base,teacher,"BUILDER_SAVE",mapOf("draftId",saved,"expectedRevision",2,"payload",mapOf("code","ARR001"))).status()!=200,"published code cannot be stolen");
   check(send(base,teacher,"BUILDER_SAVE",mapOf("draftId",saved,"expectedRevision",2,"payload",mapOf("ownerId","FORGED"))).status()!=200,"client cannot forge metadata owner");
   check(send(base,teacher,"BUILDER_SAVE",mapOf("draftId",saved,"expectedRevision",2,"payload",mapOf("weekIds",List.of("NOT_REAL")))).status()!=200,"unknown Week rejected without creating it");
   check(number(obj(assign(base,teacher,"BUILDER_GET",mapOf("draftId",saved)).get("draft")),"revision",0)==2,"failed saves do not mutate revision");
   check(send(base,teacher,"BUILDER_CREATE",mapOf("requestKey","duplicate-code-0001","payload",mapOf("code",code))).status()!=200,"reserved draft code unique in transaction");
   Map<String,Object> restore=obj(assign(base,teacher,"BUILDER_RESTORE",mapOf("draftId",saved,"expectedRevision",2,"sourceRevision",1)).get("draft"));check(number(restore,"revision",0)==3&&"Incomplete draft".equals(restore.get("title")),"restore creates new revision without deleting history");
   try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+db.toAbsolutePath());PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,username,password_hash,display_name,role) VALUES(?,?,?,?,?)")){p.setString(1,UUID.randomUUID().toString());p.setString(2,"builder_teacher2");p.setString(3,Passwords.hash("Teacher234!".toCharArray()));p.setString(4,"Teacher B");p.setString(5,"TEACHER");p.executeUpdate();}
   String foreign=login(base,"builder_teacher2","Teacher234!");
   byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=");
   var uploaded=binary(base,teacher,"/api/problem-builder/uploads/"+saved+"?name=image.png",png);
   check(uploaded.statusCode()==200,"authenticated binary PNG upload");
   Map<String,Object> asset=obj(obj(parse(new String(uploaded.body(),java.nio.charset.StandardCharsets.UTF_8))).get("asset"));
   String assetPath="/api/problem-builder/assets/"+text(asset,"id","");
   var downloaded=binary(base,teacher,assetPath,null);
   check(downloaded.statusCode()==200&&Arrays.equals(png,downloaded.body()),"asset bytes round trip without transformation");
   check(downloaded.headers().firstValue("X-Content-Type-Options").orElse("").equals("nosniff"),"asset response disables MIME sniffing");
   check(binary(base,student,assetPath,null).statusCode()!=200&&binary(base,foreign,assetPath,null).statusCode()!=200,"Student and foreign Teacher cannot download private attachment");
   check(binary(base,foreign,"/api/problem-builder/uploads/"+saved+"?name=image.png",png).statusCode()!=200,"foreign Teacher cannot attach file");
   check(binary(base,teacher,"/api/problem-builder/uploads/"+saved+"?name=attack.svg",png).statusCode()!=200,"active SVG rejected even with PNG signature");
   check(binary(base,teacher,"/api/problem-builder/uploads/"+saved+"?name=bad.png","not an image".getBytes()).statusCode()!=200,"MIME extension alone cannot bypass signature checks");
   check(binary(base,teacher,"/api/problem-builder/uploads/"+saved+"?name=large.png",new byte[2*1024*1024+1]).statusCode()==413,"oversized binary request rejected");
   check(rows(assign(base,teacher,"BUILDER_ASSETS",mapOf("draftId",saved)).get("assets")).size()==1,"failed uploads leave no asset rows");
   Map<String,Object> templateDraft=obj(assign(base,teacher,"BUILDER_CREATE",mapOf("requestKey","template-draft-001","payload",mapOf("title","Template Program","code","TEMPLATE001","summary","Summary","learningOutcome","Outcome","javaStructure","PROGRAM","difficulty","Dễ","estimatedMinutes",60,"statement",mapOf("description","Sum","inputFormat","Numbers","outputFormat","Sum","constraints","n <= 10")))).get("draft"));String templateId=text(templateDraft,"id","");
   check(send(base,teacher,"BUILDER_SAVE",mapOf("draftId",templateId,"expectedRevision",1,"currentStep",3,"payload",templateDraft.get("payload"))).status()!=200,"save cannot skip server step validation");
   assign(base,teacher,"BUILDER_ADVANCE",mapOf("draftId",templateId,"expectedRevision",1,"targetStep",2));assign(base,teacher,"BUILDER_ADVANCE",mapOf("draftId",templateId,"expectedRevision",2,"targetStep",3));
   var zipUpload=binary(base,teacher,"/api/problem-builder/uploads/"+templateId+"?name=template.zip",zip("Main.java","public class Main {}","src/test/java/SecretTest.java","secret","README.md","Instructions"));
   String zipId=text(obj(obj(parse(new String(zipUpload.body(),java.nio.charset.StandardCharsets.UTF_8))).get("asset")),"id","");
   var imported=obj(assign(base,teacher,"BUILDER_TEMPLATE_IMPORT",mapOf("draftId",templateId,"expectedRevision",3,"assetId",zipId)).get("draft"));
   var files=rows(obj(obj(imported.get("payload")).get("template")).get("files"));check(files.size()==3&&"JUDGE".equals(files.get(1).get("visibility")),"ZIP imports inert files and marks tests private");
   var evil=binary(base,teacher,"/api/problem-builder/uploads/"+templateId+"?name=evil.zip",zip("../escape.java","bad"));String evilId=text(obj(obj(parse(new String(evil.body(),java.nio.charset.StandardCharsets.UTF_8))).get("asset")),"id","");
   check(send(base,teacher,"BUILDER_TEMPLATE_IMPORT",mapOf("draftId",templateId,"expectedRevision",4,"assetId",evilId)).status()!=200,"ZIP traversal is rejected");
   check(number(obj(assign(base,teacher,"BUILDER_GET",mapOf("draftId",templateId)).get("draft")),"revision",0)==4,"failed ZIP import is atomic");
   boolean conflictingPaths=false;try{studyfix.assignment.persistence.ProblemTemplate.unzip(zip("Foo.java","class Foo {}","Foo.java/Bar.java","class Bar {}"));}catch(studyfix.assignment.persistence.ProblemBuilderRepository.Failure expected){conflictingPaths=true;}check(conflictingPaths,"ZIP file cannot also be parent directory");
   check(rejectsZip(zip("Main.java","a".repeat(70000))),"ZIP compressed bomb capped at decompressed limit");
   check(rejectsZip(zip("folder/","a".repeat(70000),"Main.java","class Main {}")),"directory entry cannot bypass decompression limit");
   check(rejectsZip(zip("Main.java","class Main {}","main.java","class Other {}")),"ZIP case-insensitive duplicate rejected");
   byte[] symlink=zip("Main.java","target.java");var central=java.nio.ByteBuffer.wrap(symlink).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int i=0;i+46<symlink.length;i++)if(central.getInt(i)==0x02014b50){central.putInt(i+38,0120777<<16);break;}check(rejectsZip(symlink),"ZIP Unix symbolic link rejected");
   check(send(base,teacher,"BUILDER_TEMPLATE_IMPORT",mapOf("draftId",saved,"expectedRevision",3,"assetId",zipId)).status()!=200,"ZIP asset cannot cross draft boundary");
   if(docker){
    var compile=assign(base,teacher,"BUILDER_COMPILE",mapOf("draftId",templateId,"expectedRevision",4));check("COMPILED".equals(obj(compile.get("compile")).get("verdict")),"real Docker compiles public Java without executing template");
    check("COMPILED".equals(obj(assign(base,teacher,"BUILDER_GET",mapOf("draftId",templateId)).get("compile")).get("verdict")),"compile audit persists with content hash");
    var draftPayload=obj(imported.get("payload"));draftPayload.put("template",mapOf("files",List.of(mapOf("path","Main.java","content","public class Main { broken syntax }","visibility","PUBLIC","editable",true))));assign(base,teacher,"BUILDER_SAVE",mapOf("draftId",templateId,"expectedRevision",4,"currentStep",3,"payload",draftPayload));
    check("COMPILE_ERROR".equals(obj(assign(base,teacher,"BUILDER_COMPILE",mapOf("draftId",templateId,"expectedRevision",5)).get("compile")).get("verdict")),"real Docker reports invalid Java, never fake compile success");
    check(send(base,foreign,"BUILDER_COMPILE",mapOf("draftId",templateId,"expectedRevision",5)).status()!=200,"compile respects author privacy");
   }
   for(String action:List.of("BUILDER_GET","BUILDER_SAVE","BUILDER_HISTORY","BUILDER_RESTORE","BUILDER_DELETE_DRAFT"))check(send(base,foreign,action,mapOf("draftId",saved,"expectedRevision",3,"sourceRevision",1,"payload",Map.of())).status()!=200,"foreign Teacher denied "+action);
   check(rows(assign(base,foreign,"BUILDER_LIST",Map.of()).get("drafts")).isEmpty(),"directory scoped to author");
   check(number(assign(base,teacher,"BANK_LIST",mapOf("query","Incomplete draft")),"total",-1)==0,"autosave does not insert published exercise versions");
   String modules=System.getenv("STUDYFIX_BUILDER_BROWSER_MODULES");if(modules!=null&&!modules.isBlank()){ProcessBuilder browser=new ProcessBuilder("C:/Program Files/nodejs/node.exe",Path.of("../web-client/tests/problem_builder_browser.cjs").toAbsolutePath().toString(),base);browser.environment().put("STUDYFIX_BROWSER_TEST_MODULES",modules);browser.inheritIO();Process process=browser.start();if(!process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Builder Chrome timed out");}check(process.exitValue()==0,"Builder browser uses real HTTP and database");}

  }
  try(Server core=new Server(0,db);AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),learning,0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!");Map<String,Object> row=obj(assign(base,teacher,"BUILDER_GET",mapOf("draftId",saved)).get("draft"));check(number(row,"revision",0)==3,"draft survives restart and migration rerun");
   check(send(base,teacher,"BUILDER_DELETE_DRAFT",mapOf("draftId",saved,"expectedRevision",2)).status()!=200,"stale delete rejected");assign(base,teacher,"BUILDER_DELETE_DRAFT",mapOf("draftId",saved,"expectedRevision",3));check(send(base,teacher,"BUILDER_GET",mapOf("draftId",saved)).status()!=200,"deleted draft no longer accessible");
  }
  System.out.println("ProblemBuilderIntegrationTest PASS: real HTTP/SQLite partial drafts, owner privacy, optimistic saves, retries, history, restore, code uniqueness and restart.");
 }
}
