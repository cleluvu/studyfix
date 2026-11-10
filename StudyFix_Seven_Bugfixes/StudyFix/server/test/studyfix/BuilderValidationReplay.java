package studyfix;

import studyfix.assignment.persistence.*;
import studyfix.assignment.service.ProblemBuilderService;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Explicit local maintenance replay. Only runs validation; never edits/publishes a draft. */
public final class BuilderValidationReplay {
    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("Usage: database-copy draft-id [report-file]");
        Path database=Path.of(args[0]).toAbsolutePath();if(!Files.isRegularFile(database))throw new IllegalArgumentException("Existing database required");
        String id=args[1],owner,hash;int revision;
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+database);var p=c.prepareStatement("SELECT owner_id,revision,content_hash FROM p3_builder_drafts WHERE id=? AND deleted=0")){
            p.setString(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Draft not found");owner=r.getString(1);revision=r.getInt(2);hash=r.getString(3);}
        }
        var identity=new Identity(owner,owner,"Draft owner",Identity.Role.TEACHER);var repo=new AssignmentRepository(database);
        try(var builder=new ProblemBuilderService(repo,new QuestionBankRepository(repo),"eclipse-temurin:21-jdk")){
            var response=builder.handle(mapOf("type","BUILDER_VALIDATE","draftId",id,"expectedRevision",revision),identity);
            if(!"OK".equals(response.get("status")))throw new AssertionError(response);
            String job=text(map(response.get("data")),"jobId","");long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(10);Map<String,Object> finished=null;
            while(System.nanoTime()<deadline){
                var status=map(builder.handle(mapOf("type","BUILDER_STATUS","draftId",id),identity).get("data"));
                for(Object item:list(status.get("history"))){var entry=map(item);if(job.equals(entry.get("id"))&&!Set.of("QUEUED","RUNNING").contains(text(entry,"status","")))finished=entry;}
                if(finished!=null)break;Thread.sleep(1000);
            }
            if(finished==null)throw new AssertionError("Validation job did not complete");
            var result=map(finished.get("result"));for(Object item:list(result.get("checks"))){var check=map(item);System.out.println(check.get("name")+": "+check.get("status"));}
            if(args.length>2)Files.writeString(Path.of(args[2]),stringify(result));
            var after=map(map(builder.handle(mapOf("type","BUILDER_GET","draftId",id),identity).get("data")).get("draft"));
            if(integer(after,"revision",0)!=revision||!hash.equals(after.get("contentHash")))throw new AssertionError("Draft content changed during validation");
            if(!Boolean.TRUE.equals(result.get("passed")))throw new AssertionError("Validation did not pass: "+stringify(result));
            System.out.println("BuilderValidationReplay PASS: all checks passed; draft revision/content hash unchanged; no publication requested.");
        }
    }
}
