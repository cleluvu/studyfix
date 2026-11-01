package studyfix;

import studyfix.assignment.transport.AssignmentTcpServer;
import studyfix.assignment.persistence.PracticeRepository;
import studyfix.assignment.model.GradingResult;
import java.nio.file.*;
import java.util.*;
import static studyfix.ClassroomIntegrationTest.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Real HTTP/auth/SQLite/worker tests. AC fixtures test persistence, not a simulated production grader. */
public final class PracticeIntegrationTest {
    static Response request(String base,String token,String type,Map<String,Object> fields)throws Exception {
        Map<String,Object> body=new LinkedHashMap<>(fields);body.put("type",type);return call(base,"/api/assignments",token,body);
    }
    static Map<String,Object> data(Response response){check(response.status()==200&&"OK".equals(response.body().get("status")),"practice request succeeds");return obj(response.body().get("data"));}
    public static void main(String[] args)throws Exception {
        System.setProperty("studyfix.screen.port","0");System.setProperty("studyfix.web.port","0");
        System.setProperty("studyfix.assignment.docker","studyfix-test-missing-docker-command");
        Path temp=Files.createTempDirectory(Path.of("build"),"practice-");Path db=temp.resolve("learning.db");
        try(Server core=new Server(0,temp.resolve("core.db"));AssignmentTcpServer assignments=new AssignmentTcpServer(core.gateway(),db,0,32,1,"unused");WebGatewayServer web=new WebGatewayServer(core,assignments)) {
            core.start();assignments.start();web.start();String base="http://127.0.0.1:"+web.port();
            String student=login(base,"student1","Student123!"),other=login(base,"student2","Student234!"),teacher=login(base,"teacher","Teacher123!");
            check(request(base,null,"PRACTICE_LIST",Map.of()).status()==401,"practice requires authentication");
            Map<String,Object> list=data(request(base,student,"PRACTICE_LIST",Map.of()));
            check(number(list,"total",0)>10&&rows(list.get("exercises")).size()==10,"full catalog paginated without room");
            check(core.rooms.isEmpty(),"practice never creates a fake room");
            Set<String> first=new HashSet<>();for(var row:rows(list.get("exercises")))first.add(text(row,"id",""));
            Map<String,Object> second=data(request(base,student,"PRACTICE_LIST",mapOf("page",2)));
            check(rows(second.get("exercises")).stream().noneMatch(r->first.contains(r.get("id"))),"catalog pages do not repeat exercises");
            Map<String,Object> search=data(request(base,student,"PRACTICE_LIST",mapOf("query","GIAI THUA")));
            check(rows(search.get("exercises")).stream().anyMatch(r->"MATH001".equals(r.get("id"))),"catalog searches without accents");
            Map<String,Object> a=data(request(base,student,"PRACTICE_GET",mapOf("exerciseId","MATH001")));
            Map<String,Object> b=data(request(base,teacher,"PRACTICE_GET",mapOf("exerciseId","MATH001")));
            check(a.equals(b),"teacher and student have same practice access before attempts");
            Map<String,Object> exercise=obj(a.get("exercise"));
            check(!exercise.containsKey("referenceSolution")&&!exercise.containsKey("tests")&&!exercise.containsKey("reference_solution"),"practice detail never exposes reference or hidden tests");
            check(!rows(exercise.get("publicTests")).isEmpty(),"public examples are available");
            Map<String,Object> debug=data(request(base,student,"PRACTICE_LIST",mapOf("mode","DEBUG_COLLAB")));
            String debugId=text(rows(debug.get("exercises")).getFirst(),"id","");
            Map<String,Object> debugExercise=obj(data(request(base,student,"PRACTICE_GET",mapOf("exerciseId",debugId))).get("exercise"));
            check(!text(debugExercise,"starterCode","").isBlank(),"debug practice uses existing starter");
            check(request(base,student,"CATALOG_LIST",Map.of()).status()==409,"room-only teacher tools retain membership check");
            check(request(base,student,"PRACTICE_SUBMIT",mapOf("exerciseId","MATH001","sourceCode","","idempotencyKey","bad")).status()==400,"empty source rejected");
            check(request(base,student,"PRACTICE_SUBMIT",mapOf("exerciseId","MATH001","sourceCode","x".repeat(32001),"idempotencyKey","large")).status()==400,"oversize source rejected");
            Map<String,Object> fields=mapOf("exerciseId","MATH001","sourceCode","public class Main {}","idempotencyKey","one-request");
            Map<String,Object> submitted=obj(data(request(base,student,"PRACTICE_SUBMIT",fields)).get("submission"));String id=text(submitted,"id","");
            check(number(submitted,"version",0)==1&&!submitted.containsKey("userId"),"official submit gets private version one");
            check(id.equals(obj(data(request(base,student,"PRACTICE_SUBMIT",fields)).get("submission")).get("id")),"retry is idempotent");
            check(request(base,student,"PRACTICE_SUBMIT",mapOf("exerciseId","MATH001","sourceCode","changed","idempotencyKey","one-request")).status()==400,"key cannot be reused for different source");
            check(request(base,other,"PRACTICE_RESULT",mapOf("submissionId",id)).status()==400,"another student cannot inspect source");
            check(request(base,teacher,"PRACTICE_RESULT",mapOf("submissionId",id)).status()==400,"teacher practice does not grant access to other users");
            RoomLifecycleIntegrationTest.until(()->"DONE".equals(obj(obj(request(base,student,"PRACTICE_RESULT",mapOf("submissionId",id)).body().get("data")).get("submission")).get("status")),5);
            Map<String,Object> result=obj(data(request(base,student,"PRACTICE_RESULT",mapOf("submissionId",id))).get("submission"));
            check(Set.of("INTERNAL_ERROR","SANDBOX_UNAVAILABLE").contains(result.get("verdict"))&&fields.get("sourceCode").equals(result.get("sourceCode")),"missing Docker is service error and source snapshot is exact");
            Map<String,Object> summary=data(request(base,student,"PRACTICE_LIST",mapOf("query","MATH001")));
            check("ERROR".equals(rows(summary.get("exercises")).getFirst().get("practiceStatus")),"infrastructure failure is not a red wrong answer");
            Map<String,Object> run=obj(data(request(base,student,"PRACTICE_RUN",mapOf("exerciseId","MATH001","sourceCode","public class Main {}","idempotencyKey","run"))).get("submission"));
            check(Boolean.TRUE.equals(run.get("isRun"))&&number(run,"version",-1)==0,"run has no official version");
            check(number(data(request(base,student,"PRACTICE_SUBMISSIONS",mapOf("exerciseId","MATH001"))),"total",0)==1,"run excluded from official history");
            check(request(base,teacher,"PRACTICE_SUBMIT",fields).status()==200,"teacher can submit with same key in own scope");

            Identity who=core.clients.values().stream().map(ClientHandler::identity).filter(i->i!=null&&i.username().equals("student1")).findFirst().orElseThrow();
            PracticeRepository store=new PracticeRepository(db);String acceptedId="";
            for(int i=1;i<=14;i++) {
                Map<String,Object> row=store.create(who,"MATH002","fixture-source-v"+i,false,"fixture-"+i,3);
                check(number(row,"version",0)==i,"versions increase per account and problem");
                String verdict=i==3?"ACCEPTED":i==2?"COMPILE_ERROR":"WRONG_ANSWER";
                store.finish(text(row,"id",""),new GradingResult(verdict,i==3?100:0,100,i==3?3:0,3,"fixture",1,List.of(mapOf("hidden",true,"passed",false,"message","SECRET_HIDDEN_OUTPUT"))));
                if(i==3)acceptedId=text(row,"id","");
            }
            Map<String,Object> progress=data(request(base,student,"PRACTICE_LIST",mapOf("filterStatus","AC")));
            check(rows(progress.get("exercises")).stream().anyMatch(r->"MATH002".equals(r.get("id"))),"AC stays green after later wrong submissions");
            Map<String,Object> oldPage=data(request(base,student,"PRACTICE_SUBMISSIONS",mapOf("exerciseId","MATH002","page",2)));
            check(number(oldPage,"total",0)==14&&rows(oldPage.get("submissions")).size()==4,"full version history paginated");
            Map<String,Object> snapshot=obj(data(request(base,student,"PRACTICE_RESULT",mapOf("submissionId",acceptedId))).get("submission"));
            check("fixture-source-v3".equals(snapshot.get("sourceCode"))&&"ACCEPTED".equals(snapshot.get("verdict")),"old version source and verdict immutable");
            check(!snapshot.toString().contains("SECRET_HIDDEN_OUTPUT"),"hidden diagnostics sanitized");
            check(number(data(request(base,other,"PRACTICE_SUBMISSIONS",mapOf("exerciseId","MATH002"))),"total",-1)==0,"history isolated by account");
            check(!new PracticeRepository(db).get(acceptedId).isEmpty(),"practice data survives repository restart");
            Map<String,Object> queued=store.create(who,"MATH002","recover fixture",true,"recover",2);String queuedId=text(queued,"id","");store.running(queuedId);
            check(new PracticeRepository(db).recover().contains(queuedId),"interrupted practice job is recovered for shared queue");
        }
        System.out.println("PracticeIntegrationTest: "+ClassroomIntegrationTest.checks+" checks PASS (HTTP/SQLite/worker; AC fixtures are persistence tests).");
    }
}
