package studyfix;
import studyfix.assignment.persistence.*;
import studyfix.assignment.model.ContestSubmissionRecord;
import studyfix.assignment.transport.AssignmentTcpServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.ExamModeIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP/SQLite scoring; completed submission scores below are explicit fixtures. */
public final class EqualTenScoringIntegrationTest {
 static void answers(AssignmentRepository repo,String contest,String user,int correct,int assigned)throws Exception{
  for(int i=1;i<=correct;i++){String key=UUID.randomUUID().toString();double max=10d/assigned;repo.createContestSubmission(new ContestSubmissionRecord(key,contest,"TEN_"+i,"test-room",user,user,"fixture",System.currentTimeMillis(),"DONE",max,max,"ACCEPTED",2,2,"Explicit completed-result fixture",0,key),"TEN_"+i);}
 }
 static double score(AssignmentRepository repo,String contest,String user)throws Exception{return ((Number)repo.leaderboard(contest).stream().filter(r->user.equals(r.get("userId"))).findFirst().orElseThrow().get("score")).doubleValue();}
 public static void main(String[] args)throws Exception{
  System.setProperty("studyfix.legacy.sqlite","true");System.setProperty("studyfix.test.accounts",Path.of("test/fixtures/accounts.tsv").toAbsolutePath().toString());System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");System.setProperty("studyfix.web.ws.port","0");Path temp=Files.createTempDirectory(Path.of("build"),"equal-ten-");
  try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignment=new AssignmentTcpServer(core.gateway(),temp.resolve("learning.db"),0,8,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignment)){
   core.start();assignment.start();web.start();String base="http://127.0.0.1:"+web.port(),teacher=login(base,"teacher","Teacher123!");
   List<Object> items=new ArrayList<>();for(int i=1;i<=7;i++){assign(base,teacher,"CATALOG_CREATE",QuestionBankIntegrationTest.question("TEN_"+i,"Ten-scale question "+i,"PUBLISHED"));items.add(ExamBankIntegrationTest.item("TEN_"+i,99));}
   var doc=ExamBankIntegrationTest.doc(items.subList(0,5));doc.put("scoringMode","EQUAL_TEN");doc.put("totalPoints",999);
   var exam=ExamBankIntegrationTest.approve(base,teacher,ExamBankIntegrationTest.save(base,teacher,"TEN_SCALE",doc));
   check(((Number)exam.get("totalPoints")).doubleValue()==10,"server fixes final scale at ten");check(list(exam.get("items")).stream().allMatch(v->((Number)map(v).get("points")).doubleValue()==1),"server fixes every raw question at one point");
   var repo=new AssignmentRepository(temp.resolve("learning.db"));long now=System.currentTimeMillis();String all=repo.createContestFromExam("TEN_ALL","test-room","Ten points","teacher",now,now,now+600000,exam,"ALL",5);
   answers(repo,all,"four-correct",4,5);answers(repo,all,"five-correct",5,5);check(score(repo,all,"four-correct")==8,"4 of 5 correct scores eight");check(score(repo,all,"five-correct")==10,"5 of 5 correct scores ten");
   String random=repo.createContestFromExam("TEN_RANDOM","test-room","Random three","teacher",now,now,now+600000,exam,"RANDOM",3);answers(repo,random,"three-correct",3,3);check(score(repo,random,"three-correct")==10,"random allocation scales against assigned count, not pool size");
   var sevenDoc=ExamBankIntegrationTest.doc(items);sevenDoc.put("scoringMode","EQUAL_TEN");var seven=ExamBankIntegrationTest.approve(base,teacher,ExamBankIntegrationTest.save(base,teacher,"TEN_SEVEN",sevenDoc));String sevenContest=repo.createContestFromExam("TEN_SEVEN_CONTEST","test-room","Seven","teacher",now,now,now+600000,seven,"ALL",7);answers(repo,sevenContest,"all-seven",7,7);check(score(repo,sevenContest,"all-seven")==10,"non-divisible question counts still yield exactly ten");check(ExamScoring.result(10.0001)==10,"rounding never exceeds ten");
   var eo=new ExamOrganizationRepository(repo);Identity owner=core.store().login("teacher","Teacher123!".toCharArray());var scheduledDoc=mapOf("title","Equal ten organization","code","EO_TEN","startAt",now+3600000,"durationMinutes",60,"distributionMode","RANDOM","problemsPerStudent",3,"policy",Map.of());var draft=eo.saveDraft(owner,"",0,scheduledDoc);var scheduled=eo.schedule(owner,text(draft,"id",""),integer(draft,"revision",0),exam,List.of(),List.of(mapOf("id","test-class")));String eid=text(scheduled,"id","");
   try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+temp.resolve("learning.db"));PreparedStatement p=c.prepareStatement("SELECT max_score FROM p3_contest_problems WHERE contest_id=?")){p.setString(1,eid);try(ResultSet r=p.executeQuery()){check(r.next()&&Math.abs(r.getDouble(1)-10d/3)<1e-9,"scheduled random exam uses the same ten-point conversion");}}
   check(((Number)eo.require(owner,eid,true).get("totalPoints")).doubleValue()==10,"scheduled exam exposes a maximum of ten");
   List<Object> weightedItems=List.of(ExamBankIntegrationTest.item("TEN_1",10),ExamBankIntegrationTest.item("TEN_2",20),ExamBankIntegrationTest.item("TEN_3",30),ExamBankIntegrationTest.item("TEN_4",40));
   var weightedDoc=ExamBankIntegrationTest.doc(weightedItems);weightedDoc.put("scoringMode","WEIGHTED_TEN");
   var weighted=ExamBankIntegrationTest.approve(base,teacher,ExamBankIntegrationTest.save(base,teacher,"WEIGHTED_TEN_SCALE",weightedDoc));
   check(((Number)map(list(weighted.get("items")).get(3)).get("points")).doubleValue()==40,"editable raw question scores survive save and approval");
   String weightedContest=repo.createContestFromExam("WEIGHTED_TEN_CONTEST","test-room","Weighted ten","teacher",now,now,now+600000,weighted,"ALL",4);
   for(int i=0;i<4;i++){var it=map(list(weighted.get("items")).get(i));String key=UUID.randomUUID().toString();double max=ExamScoring.questionMax(weighted,it,4);check(Math.abs(max-(i+1))<1e-9,"raw weights 10/20/30/40 normalize to 1/2/3/4");repo.createContestSubmission(new ContestSubmissionRecord(key,weightedContest,"TEN_"+(i+1),"test-room","weighted-user","weighted-user","fixture",now,"DONE",max,max,"ACCEPTED",2,2,"Explicit completed-result fixture",0,key),"TEN_"+(i+1));if(i==2)check(score(repo,weightedContest,"weighted-user")==6,"60 of 100 raw points gives six out of ten");}
   check(score(repo,weightedContest,"weighted-user")==10,"full weighted score is exactly ten");
   var invalid=ExamBankIntegrationTest.doc(List.of(ExamBankIntegrationTest.item("TEN_1",0)));invalid.put("scoringMode","WEIGHTED_TEN");check(send(base,teacher,"EXAMBANK_SAVE",mapOf("code","INVALID_WEIGHTED","document",invalid)).status()!=200,"zero question weight is rejected");
  }
  System.out.println("EqualTenScoringIntegrationTest PASS: one-point weights, 4/5=8, 5/5=10, random allocation, rounding and scheduled exams.");
 }
}
