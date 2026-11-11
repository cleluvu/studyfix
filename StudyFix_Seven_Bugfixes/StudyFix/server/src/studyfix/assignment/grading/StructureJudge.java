package studyfix.assignment.grading;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import studyfix.assignment.model.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Bounded, isolated javac AST inspection. Student code is never loaded by the server. */
public final class StructureJudge {
 private final DockerJavaSandbox docker;
 public StructureJudge(String image){docker=new DockerJavaSandbox(image,3,90);}
 public Map<String,Object> inspect(Exercise exercise,String source,Map<String,Object> config,Map<String,Object> rules)throws Exception {
  if(source==null||source.getBytes(StandardCharsets.UTF_8).length>32768)return mapOf("error","COMPILE_ERROR","checks",List.of());
  Path work=DockerJavaSandbox.createWorkspace("studyfix-structure-");long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DockerJavaSandbox.jobWallMillis());
  try {
   Files.createDirectory(work.resolve("judge"));Files.createDirectory(work.resolve("judge-classes"));
   for(String file:List.of("StructureInspector","AssignmentJson")){try(var in=StructureJudge.class.getResourceAsStream("/studyfix/assignment/grading/inspector-sources/"+file+".java.txt")){if(in==null)throw new IllegalStateException("Thiếu mã bộ kiểm tra cấu trúc trong bản build.");Files.copy(in,work.resolve("judge/"+file+".java"));}}
   String entry=exercise.entryFile();if(!entry.matches("[A-Za-z][A-Za-z0-9_]*\\.java"))throw new IllegalArgumentException("Invalid entry file");Files.writeString(work.resolve(entry),source,StandardCharsets.UTF_8);List<String> files=new ArrayList<>(List.of("/code/"+entry));
   for(Object value:list(config.get("support_files"))){var f=map(value);String name=text(f,"path","");if(!name.matches("[A-Za-z][A-Za-z0-9_]*\\.java")||name.equals(entry))throw new IllegalArgumentException("Invalid support file");Files.writeString(work.resolve(name),text(f,"content",""),StandardCharsets.UTF_8);files.add("/code/"+name);}
   Files.writeString(work.resolve("rules.json"),stringify(rules),StandardCharsets.UTF_8);
   String name=DockerJavaSandbox.containerName("structure-compile");List<String> command=docker.dockerBase(name,work,false,256);command.addAll(List.of("javac","-J-Xmx128m","--release","17","-proc:none","-encoding","UTF-8","-d","judge-classes","judge/StructureInspector.java","judge/AssignmentJson.java"));
   var compiled=docker.runBudgeted(command,null,30000,12000,12000,name,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
   if(compiled.exitCode()!=0||compiled.timedOut()||compiled.startFailure()!=null)return mapOf("error","SANDBOX_UNAVAILABLE","checks",List.of());
   name=DockerJavaSandbox.containerName("structure-analyze");command=docker.dockerBase(name,work,true,256);command.addAll(List.of("java","-Xmx128m","-XX:ActiveProcessorCount=1","-cp","/code/judge-classes","studyfix.assignment.grading.StructureInspector","/code/rules.json"));command.addAll(files);
   var result=docker.runBudgeted(command,null,15000,128*1024,12000,name,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
   if(result.exitCode()!=0||result.timedOut()||result.outputExceeded()||result.startFailure()!=null)return mapOf("error","SANDBOX_UNAVAILABLE","checks",List.of());
   return object(result.stdoutText());
  }finally{DockerJavaSandbox.removeWorkspace(work);}
 }
 public static GradingResult combine(GradingResult behavior,Map<String,Object> rules,List<Map<String,Object>> checks,double max,long duration){
  double total=checks.stream().mapToDouble(c->((Number)c.get("weight")).doubleValue()).sum(),earned=checks.stream().filter(c->Boolean.TRUE.equals(c.get("passed"))).mapToDouble(c->((Number)c.get("weight")).doubleValue()).sum(),share=((Number)rules.get("weight")).doubleValue()/100;
  boolean all=checks.stream().allMatch(c->Boolean.TRUE.equals(c.get("passed")));double score=max*(share*(total==0?0:earned/total)+(1-share)*(behavior.maxScore()==0?0:behavior.score()/behavior.maxScore()));
  if(Set.of("SANDBOX_UNAVAILABLE","INTERNAL_ERROR").contains(behavior.verdict()))score=0;
  if(Boolean.TRUE.equals(rules.get("gate"))&&!all)score=0;
  String verdict=all?behavior.verdict():Set.of("SANDBOX_UNAVAILABLE","INTERNAL_ERROR").contains(behavior.verdict())?behavior.verdict():"STRUCTURE_ERROR";
  List<Map<String,Object>> report=new ArrayList<>();for(var check:checks){var row=new LinkedHashMap<>(check);double points=total==0?0:max*share*((Number)check.get("weight")).doubleValue()/total;row.put("maxPoints",Math.round(points*10000d)/10000d);row.put("points",Boolean.TRUE.equals(check.get("passed"))?Math.round(points*10000d)/10000d:0);report.add(row);}
  return new GradingResult(verdict,Math.round(score*10000d)/10000d,max,behavior.passed(),behavior.total(),"Cấu trúc: "+checks.stream().filter(c->Boolean.TRUE.equals(c.get("passed"))).count()+"/"+checks.size()+" tiêu chí. "+behavior.feedback(),duration,behavior.publicTests(),report);
 }
}
