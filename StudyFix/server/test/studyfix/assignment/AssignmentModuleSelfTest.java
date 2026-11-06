package studyfix.assignment;

import studyfix.assignment.catalog.SeedExerciseCatalog;
import studyfix.assignment.model.*;
import studyfix.assignment.persistence.AssignmentRepository;
import studyfix.assignment.protocol.AssignmentJson;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Kiểm tra nhanh module bằng file SQLite tạm, không tác động đến data/studyfix.db. */
public final class AssignmentModuleSelfTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Object parsed=AssignmentJson.parse("{\"s\":\"tiếng Việt\\nnext\",\"n\":2,\"ok\":true,\"a\":[1,null]}");
  check(parsed instanceof Map<?,?>,"JSON object roundtrip parse");
  Map<String,Object> src=AssignmentJson.object(AssignmentJson.stringify(AssignmentJson.mapOf("v","dòng 1\ndòng 2","n",4,"b",true)));
  check("dòng 1\ndòng 2".equals(src.get("v")),"JSON UTF-8/newline");
  Path temp=Files.createTempDirectory("studyfix-p3-test-");Path db=temp.resolve("learning.db");
  try{
   AssignmentRepository repo=new AssignmentRepository(db);List<Exercise> seed=SeedExerciseCatalog.all();check(seed.size()>=20,"catalog includes both original and additional exercises");
   long debugCount=seed.stream().filter(e->e.mode()==ExerciseMode.DEBUG_COLLAB).count();check(debugCount>=8&&debugCount<=12,"catalog seeds 8-12 DEBUG_COLLAB tasks");check(seed.stream().filter(e->e.mode()==ExerciseMode.DEBUG_COLLAB).allMatch(e->!e.starterCode().isBlank()&&!e.starterCode().equals(e.referenceSolution())&&e.tests().stream().anyMatch(Exercise.TestCase::hidden)),"each debug exercise has a distinct buggy starter and hidden tests");
   repo.seed(seed);repo.seed(seed);check(repo.listExercises("","").size()==seed.size(),"catalog seeding is idempotent");check(repo.listExercises("","","DEBUG_COLLAB").size()==debugCount,"catalog filters DEBUG_COLLAB by explicit mode");check(repo.listExercises("","","BUILD_FROM_SCRATCH").size()==seed.size()-debugCount,"catalog filters BUILD_FROM_SCRATCH by explicit mode");
   Exercise e=repo.getExercise("ARR001");check(e!=null&&e.tests().size()>=2&&e.mode()==ExerciseMode.BUILD_FROM_SCRATCH,"scratch exercise and test cases persisted");check(!e.publicView().containsKey("referenceSolution"),"reference solution excluded from public view");Exercise debug=repo.getExercise("DBG_ARR001");check(debug!=null&&debug.mode()==ExerciseMode.DEBUG_COLLAB&&debug.publicView().containsKey("starterCode")&&!debug.publicView().containsKey("referenceSolution"),"debug starter is public but solution remains private");
   long now=System.currentTimeMillis();AssignmentRecord a=new AssignmentRecord("a-1","ARR001","room-1","teacher-1","ROOM",null,null,now,0,"OPEN");repo.createAssignment(a);check(repo.getAssignment("a-1")!=null&&repo.getAssignment("a-1").mode()==ExerciseMode.BUILD_FROM_SCRATCH,"scratch assignment and its mode stored");
   AssignmentRecord snapA=new AssignmentRecord("a-snapshot","ARR001","room-1","teacher-1","ROOM",null,null,now,0,"OPEN");repo.createAssignment(snapA,List.of(new AssignmentRepository.AssignmentRecipient("student-u1","student1","Student One"),new AssignmentRepository.AssignmentRecipient("student-u2","student2","Student Two")));check(repo.hasAssignmentRecipientSnapshot("a-snapshot")&&repo.listAssignmentRecipients("a-snapshot").size()==2,"assignment recipient snapshot persists at creation time");
   AssignmentRecord emptySnap=new AssignmentRecord("a-empty-snapshot","ARR001","room-1","teacher-1","ROOM",null,null,now,0,"OPEN");repo.createAssignment(emptySnap,List.of());check(repo.hasAssignmentRecipientSnapshot("a-empty-snapshot")&&repo.listAssignmentRecipients("a-empty-snapshot").isEmpty(),"empty room recipient snapshot remains distinguishable from legacy assignment");
   AssignmentRecord da=new AssignmentRecord("a-debug","DBG_ARR001","room-1","teacher-1","ROOM",null,null,now,0,"OPEN",ExerciseMode.DEBUG_COLLAB);repo.createAssignment(da);check(repo.getAssignment("a-debug").mode()==ExerciseMode.DEBUG_COLLAB,"assignment mode snapshot survives persistence");
   SharedCheckRun run=new SharedCheckRun("check-1","check-key-1","a-debug","room-1",7,"sha256-test","public class Main {}","student-1","student1",now,"QUEUED","QUEUED",0,100,0,3,"queued",0);repo.createSharedCheck(run);check(repo.sharedCheckByRequestKey("check-key-1").id().equals("check-1"),"shared check idempotency key persisted");check(repo.latestSharedCheck("a-debug","room-1").checkedVersion()==7,"latest shared check preserves code version");repo.saveSharedCheckResult("check-1","PASS","ACCEPTED",100,100,3,3,"all tests passed",10);SharedCheckRun finished=repo.getSharedCheck("check-1");check("PASS".equals(finished.status())&&finished.passedTests()==3,"shared check result persists independently from submissions");
   SubmissionRecord s=new SubmissionRecord("s-1","a-1","room-1","student-1","student1","public class Main {}",now,"QUEUED",0,100,"QUEUED",0,e.tests().size(),"queued",0,"PERSONAL","idemp-1");repo.createSubmission(s);check(repo.byIdempotency("idemp-1").id().equals("s-1"),"idempotency key lookup");check("PERSONAL".equals(repo.getSubmission("s-1").submissionMode()),"submission mode survives persistence");
   List<Map<String,Object>> caseRows=new ArrayList<>();caseRows.add(AssignmentJson.mapOf("hidden",false,"passed",true,"message","pass","durationMs",2));caseRows.add(AssignmentJson.mapOf("hidden",true,"passed",false,"message","private case expected output","durationMs",4));repo.saveResult("s-1",new GradingResult("WRONG_ANSWER",50,100,1,2,"1/2 passed",6,caseRows));
   List<Map<String,Object>> shown=repo.publicTestResults("s-1");check(shown.size()==2,"test results persisted");check(!String.valueOf(shown.get(1).get("message")).contains("private case expected output"),"hidden output is never returned");
   String contest=repo.createContest("c-1","room-1","Exam","teacher-1",now,now,now+60000,List.of("a-1"));
   SubmissionRecord cs=new SubmissionRecord("s-2","a-1","room-1","student-2","student2","public class Main {}",now,"QUEUED",0,100,"QUEUED",0,e.tests().size(),"queued",0,"PERSONAL","idemp-2");repo.createSubmission(cs,contest);repo.saveResult("s-2",new GradingResult("ACCEPTED",100,100,3,3,"all passed",10,List.of(AssignmentJson.mapOf("hidden",false,"passed",true,"message","pass","durationMs",3))));
   List<Map<String,Object>> board=repo.leaderboard(contest);check(board.size()==1&&"student2".equals(board.get(0).get("username")),"legacy contest leaderboard reads legacy assignment submissions only");check(((Number)board.get(0).get("score")).doubleValue()==100,"legacy leaderboard score");
   String directContest=repo.createContestFromExercises("c-direct","room-1","Catalog exam","teacher-1",now,now,now+60000,"INDIVIDUAL",List.of("ARR001"));
   check(repo.contestAssignments(directContest).isEmpty(),"catalog contest does not require or create an assignment");
   check(repo.contestExerciseIds(directContest).equals(List.of("ARR001")),"catalog contest stores the selected exercise ID");
   check(repo.contestProblemId(directContest,"ARR001").equals("ARR001"),"catalog contest maps problem to the exercise bank");
   check("AC_COUNT_SCORE_TIME_V2".equals(repo.contest(directContest).get("scoringRule")),"new catalog contest versions the AC-first scoring rule");
   ContestSubmissionRecord contestSubmission=new ContestSubmissionRecord("cs-1",directContest,"ARR001","room-1","student-3","student3","public class Main {}",now,"QUEUED",0,100,"QUEUED",0,e.tests().size(),"queued",0,"contest-idemp-1");
   repo.createContestSubmission(contestSubmission,"ARR001");check(repo.contestSubmissionByIdempotency("contest-idemp-1").id().equals("cs-1"),"contest submissions have independent idempotency storage");
   repo.saveContestSubmissionResult("cs-1",new GradingResult("ACCEPTED",100,100,e.tests().size(),e.tests().size(),"all passed",9,List.of(AssignmentJson.mapOf("hidden",false,"passed",true,"message","pass","durationMs",2))));
   check(repo.contestSubmissionTestResults("cs-1").size()==1,"contest test results are persisted separately from assignment submissions");
   List<Map<String,Object>> directBoard=repo.leaderboard(directContest);check(directBoard.size()==1&&"student3".equals(directBoard.get(0).get("username")),"catalog contest leaderboard reads independent contest submissions");
   check(((Number)directBoard.get(0).get("acceptedCount")).intValue()==1,"new leaderboard ranks by accepted exercise count");
   checkLegacyMigration(temp.resolve("legacy.db"));
   checkContestMigration(temp.resolve("contest-v3.db"));
   System.out.println("AssignmentModuleSelfTest PASS: "+checks+" checks");
  } finally {try(var walk=Files.walk(temp)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 }
 private static void checkLegacyMigration(Path legacyDb)throws Exception{
  String url="jdbc:sqlite:"+legacyDb.toAbsolutePath();String template="public class Main {}";
  try(Connection c=DriverManager.getConnection(url);Statement st=c.createStatement()){
   st.executeUpdate("CREATE TABLE p3_schema_meta(version INTEGER NOT NULL)");st.executeUpdate("INSERT INTO p3_schema_meta(version) VALUES(2)");
   st.executeUpdate("CREATE TABLE p3_exercises(id TEXT PRIMARY KEY,title TEXT NOT NULL,topic TEXT NOT NULL,difficulty TEXT NOT NULL,statement TEXT NOT NULL,input_format TEXT NOT NULL,output_format TEXT NOT NULL,constraints_text TEXT NOT NULL,template TEXT NOT NULL,hints TEXT NOT NULL,reference_solution TEXT NOT NULL,catalog_version INTEGER NOT NULL,created_at INTEGER NOT NULL)");
   st.executeUpdate("CREATE TABLE p3_assignments(id TEXT PRIMARY KEY,exercise_id TEXT NOT NULL,room_id TEXT NOT NULL,created_by_user_id TEXT NOT NULL,scope TEXT NOT NULL,target_user_id TEXT,target_username TEXT,created_at INTEGER NOT NULL,deadline_at INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN')");
   st.executeUpdate("CREATE TABLE p3_test_cases(id INTEGER PRIMARY KEY AUTOINCREMENT,exercise_id TEXT NOT NULL,ordinal_no INTEGER NOT NULL,input_text TEXT NOT NULL,expected_output TEXT NOT NULL,weight INTEGER NOT NULL,hidden INTEGER NOT NULL,UNIQUE(exercise_id,ordinal_no))");
   try(PreparedStatement p=c.prepareStatement("INSERT INTO p3_exercises VALUES('LEGACY','Legacy task','Mảng','Dễ','Statement','Input','Output','Constraints',?,'Hint','Solution',1,1)")){p.setString(1,template);p.executeUpdate();}
   st.executeUpdate("INSERT INTO p3_assignments VALUES('legacy-assignment','LEGACY','legacy-room','legacy-teacher','ROOM',NULL,NULL,1,0,'OPEN')");
  }
  AssignmentRepository migrated=new AssignmentRepository(legacyDb);Exercise legacy=migrated.getExercise("LEGACY");check(legacy!=null&&legacy.mode()==ExerciseMode.BUILD_FROM_SCRATCH,"migration defaults legacy exercises to BUILD_FROM_SCRATCH");check(template.equals(legacy.starterCode()),"migration fills legacy starter_code from template");check(migrated.getAssignment("legacy-assignment").mode()==ExerciseMode.BUILD_FROM_SCRATCH,"migration keeps legacy assignments usable");
  try(Connection c=DriverManager.getConnection(url);Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT version FROM p3_schema_meta LIMIT 1")){check(rs.next()&&rs.getInt(1)==6,"learning database schema migration advances version to 6");}
 }
 private static void checkContestMigration(Path db)throws Exception{
  String url="jdbc:sqlite:"+db.toAbsolutePath();
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement()){
   s.executeUpdate("PRAGMA foreign_keys=OFF");
   s.executeUpdate("CREATE TABLE p3_schema_meta(version INTEGER NOT NULL)");s.executeUpdate("INSERT INTO p3_schema_meta(version) VALUES(3)");
   s.executeUpdate("CREATE TABLE p3_exercises(id TEXT PRIMARY KEY,title TEXT NOT NULL,topic TEXT NOT NULL,difficulty TEXT NOT NULL,statement TEXT NOT NULL,input_format TEXT NOT NULL,output_format TEXT NOT NULL,constraints_text TEXT NOT NULL,template TEXT NOT NULL,hints TEXT NOT NULL,reference_solution TEXT NOT NULL,catalog_version INTEGER NOT NULL,created_at INTEGER NOT NULL,mode TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH',starter_code TEXT NOT NULL DEFAULT '')");
   s.executeUpdate("INSERT INTO p3_exercises VALUES('LEGACY','Legacy contest task','Arrays','Easy','statement','input','output','constraints','template','hint','solution',7,1,'BUILD_FROM_SCRATCH','template')");
   s.executeUpdate("CREATE TABLE p3_assignments(id TEXT PRIMARY KEY,exercise_id TEXT NOT NULL,room_id TEXT NOT NULL,created_by_user_id TEXT NOT NULL,scope TEXT NOT NULL,target_user_id TEXT,target_username TEXT,created_at INTEGER NOT NULL,deadline_at INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'OPEN',mode TEXT NOT NULL DEFAULT 'BUILD_FROM_SCRATCH')");
   s.executeUpdate("INSERT INTO p3_assignments VALUES('legacy-assignment','LEGACY','legacy-room','legacy-teacher','ROOM',NULL,NULL,1,0,'OPEN','BUILD_FROM_SCRATCH')");
   s.executeUpdate("CREATE TABLE p3_contests(id TEXT PRIMARY KEY,room_id TEXT NOT NULL,title TEXT NOT NULL,created_by_user_id TEXT NOT NULL,created_at INTEGER NOT NULL,start_at INTEGER NOT NULL,end_at INTEGER NOT NULL,status TEXT NOT NULL,scoring_rule_version TEXT NOT NULL)");
   s.executeUpdate("INSERT INTO p3_contests VALUES('legacy-contest','legacy-room','Legacy exam','legacy-teacher',1,1,90000,'RUNNING','BEST_SCORE_EARLIEST_ACHIEVED_V1')");
   s.executeUpdate("CREATE TABLE p3_contest_problems(contest_id TEXT NOT NULL REFERENCES p3_contests(id) ON DELETE CASCADE,assignment_id TEXT NOT NULL REFERENCES p3_assignments(id),max_score REAL NOT NULL,PRIMARY KEY(contest_id,assignment_id))");
   s.executeUpdate("INSERT INTO p3_contest_problems VALUES('legacy-contest','legacy-assignment',100)");
  }
  AssignmentRepository migrated=new AssignmentRepository(db);
  check(migrated.contestExerciseIds("legacy-contest").equals(List.of("LEGACY")),"schema v3 migration preserves contest exercise association");
  check(migrated.contestAssignments("legacy-contest").equals(List.of("legacy-assignment")),"schema v3 migration preserves the old assignment link");
  check("BEST_SCORE_EARLIEST_ACHIEVED_V1".equals(migrated.contest("legacy-contest").get("scoringRule")),"schema v3 migration preserves legacy scoring rule");
  try(Connection c=DriverManager.getConnection(url);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT version FROM p3_schema_meta LIMIT 1")){check(r.next()&&r.getInt(1)==6,"learning migration advances schema v3 to v6");}
 }

}
