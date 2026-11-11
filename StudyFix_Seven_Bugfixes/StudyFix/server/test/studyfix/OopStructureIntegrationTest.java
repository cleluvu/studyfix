package studyfix;
import studyfix.assignment.transport.AssignmentTcpServer;
import studyfix.assignment.grading.*;
import studyfix.assignment.service.*;
import studyfix.assignment.persistence.*;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real Docker AST inspection, HTTP Builder publication, student receipts and SQLite persistence. */
public final class OopStructureIntegrationTest {
 static final String GOOD="public class Student {private final String name;private final double score;public Student(String name,double score){this.name=name;this.score=score;}public String getName(){return name;}public double getScore(){return score;}public boolean isPassed(){return score>=5;}}";
 static Map<String,Object> rules(){return mapOf("schemaVersion",1,"enabled",true,"weight",30,"gate",false,"rules",List.of(
  mapOf("id","class","kind","TYPE","owner","Student","type","CLASS","visibility","PUBLIC"),
  mapOf("id","name","kind","FIELD","owner","Student","name","name","type","java.lang.String","visibility","PRIVATE","forbiddenModifiers",List.of("STATIC")),
  mapOf("id","score","kind","FIELD","owner","Student","name","score","type","double","visibility","PRIVATE","forbiddenModifiers",List.of("STATIC")),
  mapOf("id","ctor","kind","CONSTRUCTOR","owner","Student","parameters",List.of("java.lang.String","double"),"visibility","PUBLIC"),
  mapOf("id","passed","kind","METHOD","owner","Student","name","isPassed","parameters",List.of(),"type","boolean","visibility","PUBLIC","forbiddenModifiers",List.of("STATIC"))));}
 static Map<String,Object> payload(){var p=BuilderCompleteIntegrationTest.payload("OOP_STRUCTURE","CLASS","public class Student {}",GOOD,"System.out.println(new Student(\"A\",6).isPassed());","true\n","System.out.println(new Student(\"B\",4.9).isPassed());","false\n");obj(p.get("grading")).put("structure",rules());return p;}
 static Map<String,Object> waitPractice(String base,String token,String id)throws Exception{RoomLifecycleIntegrationTest.until(()->"DONE".equals(obj(assign(base,token,"PRACTICE_RESULT",mapOf("submissionId",id)).get("submission")).get("status")),180);return obj(assign(base,token,"PRACTICE_RESULT",mapOf("submissionId",id)).get("submission"));}
 public static void main(String[] args)throws Exception {
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.management.db","sqlite");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");
  Path temp=Files.createTempDirectory(Path.of("build"),"oop-structure-");var p=payload();var exercise=BuilderProblem.exercise(p,"FIXTURE");var config=BuilderProblem.config(p);var inspector=new StructureJudge("eclipse-temurin:21-jdk");
  var report=inspector.inspect(exercise,GOOD,config,StructureRules.normalize(rules()));check(!report.containsKey("error")&&list(report.get("checks")).stream().allMatch(v->Boolean.TRUE.equals(obj(v).get("passed"))),"private final accepted, exact signatures and modifiers recognized in Docker");
  var unsafe=GOOD.replace("private final String name;","private final String name;static {if(System.nanoTime()>0)throw new RuntimeException(\"MUST_NOT_INITIALIZE\");}");check(!inspector.inspect(exercise,unsafe,config,StructureRules.normalize(rules())).containsKey("error"),"AST analysis does not initialize student classes");
  var wrong=GOOD.replace("public boolean isPassed()","static boolean isPassed()").replace("return score>=5;","return true;");var wrongReport=inspector.inspect(exercise,wrong,config,StructureRules.normalize(rules()));check(list(wrongReport.get("checks")).stream().map(v->obj(v)).anyMatch(v->v.get("ruleId").equals("passed")&&Boolean.FALSE.equals(v.get("passed"))),"static/package method rejected");
  var relations=mapOf("enabled",true,"rules",List.of(mapOf("id","parent","kind","EXTENDS","owner","Student","target","Person"),mapOf("id","interface","kind","IMPLEMENTS","owner","Student","target","Payable"),mapOf("id","override","kind","OVERRIDE","owner","Student","name","toString","target","java.lang.Object"),mapOf("id","annotation","kind","ANNOTATION","owner","Student","name","toString","target","java.lang.Override"),mapOf("id","abstract","kind","TYPE","owner","Person","type","CLASS","modifiers",List.of("ABSTRACT"))));
  var relationSource="abstract class Person {} interface Payable {} public class Student extends Person implements Payable {@Override public String toString(){return \"x\";}}";
  var relationReport=inspector.inspect(exercise,relationSource,config,StructureRules.normalize(relations));check(!relationReport.containsKey("error")&&list(relationReport.get("checks")).stream().allMatch(v->Boolean.TRUE.equals(obj(v).get("passed"))),"extends, implements, override, source annotation and abstract work");
  var noRelation=relationSource.replace("extends Person implements Payable","");var missing=inspector.inspect(exercise,noRelation,config,StructureRules.normalize(relations));check(list(missing.get("checks")).stream().filter(v->Boolean.FALSE.equals(obj(v).get("passed"))).count()==2,"unrelated class cannot satisfy extends or implements");
  try{StructureRules.normalize(mapOf("enabled",true,"rules",List.of(mapOf("id","x","kind","EXECUTE","owner","Student"))));throw new AssertionError("invalid schema accepted");}catch(IllegalArgumentException expected){check(true,"arbitrary executable rules rejected");}
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,16,1,"eclipse-temurin:21-jdk");WebGatewayServer web=new WebGatewayServer(core,assignments)){
   core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!"),student=login(base,"student1","Student123!");
   var d=obj(assign(base,teacher,"BUILDER_CREATE",mapOf("requestKey",UUID.randomUUID().toString(),"payload",p)).get("draft"));check(obj(obj(d.get("payload")).get("grading")).containsKey("structure"),"rules stored in SQLite draft");
   var validation=BuilderCompleteIntegrationTest.validate(base,teacher,d);check(Boolean.TRUE.equals(validation.get("passed")),"reference passes AST and behavior; incomplete starter detected");
   assign(base,teacher,"BUILDER_REVIEW",mapOf("draftId",d.get("id"),"expectedRevision",d.get("revision"),"approveSelf",true));String id=text(assign(base,teacher,"BUILDER_PUBLISH",mapOf("draftId",d.get("id"),"expectedRevision",d.get("revision"))),"exerciseId","");
   var repo=new AssignmentRepository(temp.resolve("learning.db"));check(Boolean.TRUE.equals(obj(new OjRepository(repo).config(id).get("structure_rules")).get("enabled")),"published OJ contract retains rules");
   String source=GOOD.replace("private final String name","public final String name").replace("private final double score","public final double score");
   String receipt=text(obj(assign(base,student,"PRACTICE_SUBMIT",mapOf("exerciseId",id,"sourceCode",source,"idempotencyKey",UUID.randomUUID().toString())).get("submission")),"id","");var result=waitPractice(base,student,receipt);check(number(result,"score",0)==88&&"STRUCTURE_ERROR".equals(result.get("verdict")),"30/70 rubric yields 88 for two public fields");check(list(result.get("structureResults")).size()==5,"student receives persisted per-rule reports");
   var cloned=obj(assign(base,teacher,"BUILDER_CREATE",mapOf("requestKey",UUID.randomUUID().toString(),"baseExerciseId",id)).get("draft"));check(Boolean.TRUE.equals(obj(obj(obj(cloned.get("payload")).get("grading")).get("structure")).get("enabled")),"editing a published problem preserves structure rules");
   String correct=text(obj(assign(base,student,"PRACTICE_SUBMIT",mapOf("exerciseId",id,"sourceCode",GOOD,"idempotencyKey",UUID.randomUUID().toString())).get("submission")),"id","");check(number(waitPractice(base,student,correct),"score",0)==100,"correct private final implementation scores 100");
   var gated=StructureRules.normalize(rules());gated.put("gate",true);var gateConfig=new LinkedHashMap<>(config);gateConfig.put("structure_rules",gated);var gate=new OjJavaSandbox("eclipse-temurin:21-jdk").grade(exercise,source,exercise.tests(),100,gateConfig);check(gate.score()==0&&gate.publicTests().isEmpty()&&gate.structureResults().size()==5,"required structure gate blocks behavior with zero score");
   if(args.length>0&&args[0].equals("--browser")){var child=new ProcessBuilder("C:/Program Files/nodejs/node.exe",Path.of("../web-client/tests/oop_structure_browser.cjs").toAbsolutePath().toString(),base).inheritIO().start();if(!child.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("OOP browser timeout");}check(child.exitValue()==0,"OOP authoring Chrome checks");}
  }
  var restored=new PracticeRepository(temp.resolve("learning.db"));try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+temp.resolve("learning.db"));var s=c.createStatement();var r=s.executeQuery("SELECT submission_id FROM p3_structure_results WHERE result_json<>'[]' LIMIT 1")){check(r.next()&&!list(restored.get(r.getString(1)).get("structureResults")).isEmpty(),"report survives reopening SQLite");}
  System.out.println("OopStructureIntegrationTest PASS: Docker AST, modifiers, relations, validation/publication, weighted scoring, gate, HTTP and SQLite reports.");
 }
}
