package studyfix.assignment.catalog;
import studyfix.Identity;
import studyfix.assignment.persistence.*;
import studyfix.assignment.grading.OjJavaSandbox;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Operator-only catalog import and sandbox verification. No student code runs on host. */
public final class OjCatalogCli {
 public static void main(String[] args)throws Exception{
  if(args.length<2)throw new IllegalArgumentException("preview ZIP | replace DB ZIP OWNER_ID | verify DB IMAGE REPORT [CODE...]");
  if(args[0].equals("preview")){System.out.println(stringify(new OjPackage(Files.readAllBytes(Path.of(args[1]))).preview()));return;}
  AssignmentRepository repo=new AssignmentRepository(Path.of(args[1]));new QuestionWeekRepository(repo);new QuestionBankRepository(repo);OjRepository oj=new OjRepository(repo);
  if(args[0].equals("replace")){if(args.length!=4)throw new IllegalArgumentException("replace DB ZIP OWNER_ID");System.out.println(stringify(oj.importPackage(new OjPackage(Files.readAllBytes(Path.of(args[2]))),new Identity(args[3],"operator","Catalog operator",Identity.Role.TEACHER),true)));return;}
  if(!args[0].equals("verify")||args.length<4)throw new IllegalArgumentException("verify DB IMAGE REPORT [CODE...]");
  Set<String> selected=new HashSet<>(Arrays.asList(args).subList(4,args.length));List<String> ids=oj.activeIds().stream().filter(id->selected.isEmpty()||selected.contains(id)).toList();List<Map<String,Object>> results=Collections.synchronizedList(new ArrayList<>());ExecutorService pool=Executors.newFixedThreadPool(2);
  try{List<Future<?>> jobs=new ArrayList<>();for(String id:ids)jobs.add(pool.submit(()->{try{
   var e=repo.getExercise(id);var config=oj.config(id);var previous=oj.validation(id);
   if(Boolean.TRUE.equals(previous.get("referencePassed"))&&Boolean.TRUE.equals(previous.get("wrongDetected"))){results.add(previous);System.out.println(id+" verified snapshot reused");return;}
   var sandbox=new OjJavaSandbox(args[2]);var reference=Boolean.TRUE.equals(previous.get("referencePassed"))?null:sandbox.grade(e,e.referenceSolution(),e.tests(),100,config);var wrong=sandbox.grade(e,e.template(),e.tests(),100,config);
   Map<String,Object> report=new LinkedHashMap<>(previous);report.put("exerciseId",id);if(reference!=null){report.put("referenceVerdict",reference.verdict());report.put("referencePassed",reference.verdict().equals("ACCEPTED"));report.put("passedTests",reference.passed());report.put("totalTests",reference.total());report.put("referenceDurationMs",reference.durationMillis());}else report.put("referenceReused",true);
   report.putAll(mapOf("wrongCandidate","provided_incomplete_starter","wrongVerdict",wrong.verdict(),"wrongDetected",!Set.of("ACCEPTED","SANDBOX_UNAVAILABLE","INTERNAL_ERROR").contains(wrong.verdict()),"validatedAt",System.currentTimeMillis(),"educationalReview","PENDING"));oj.validation(id,report);results.add(report);System.out.println(id+" reference="+report.get("referenceVerdict")+" "+report.get("passedTests")+"/"+report.get("totalTests")+" starter="+wrong.verdict());
  }catch(Exception failure){results.add(mapOf("exerciseId",id,"referencePassed",false,"error",failure.getClass().getSimpleName()));throw new RuntimeException(failure);}}));for(var job:jobs)job.get();}
  finally{pool.shutdownNow();Path report=Path.of(args[3]);Files.createDirectories(report.toAbsolutePath().getParent());results.sort(Comparator.comparing(r->text(r,"exerciseId","")));Files.writeString(report,stringify(mapOf("total",results.size(),"referencePassed",results.stream().filter(r->Boolean.TRUE.equals(r.get("referencePassed"))).count(),"wrongDetected",results.stream().filter(r->Boolean.TRUE.equals(r.get("wrongDetected"))).count(),"results",results)),java.nio.charset.StandardCharsets.UTF_8);}
  if(results.size()!=ids.size()||results.stream().anyMatch(row->!Boolean.TRUE.equals(row.get("referencePassed"))))throw new IllegalStateException("Một số reference chưa qua; đọc report, chưa phát hành.");
 }
}
