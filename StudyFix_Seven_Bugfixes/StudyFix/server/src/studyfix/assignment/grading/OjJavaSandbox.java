package studyfix.assignment.grading;
import studyfix.assignment.model.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** OJ adapters. Only the current test is delivered; expected output stays on the server. */
public final class OjJavaSandbox {
 private final DockerJavaSandbox docker;
 private static final String FORMAT="static String fmt(Object x){if(x==null)return \"null\";Class<?> c=x.getClass();if(c.isArray()){if(x instanceof int[])return java.util.Arrays.toString((int[])x);if(x instanceof long[])return java.util.Arrays.toString((long[])x);if(x instanceof Object[])return java.util.Arrays.deepToString((Object[])x);}if(x instanceof java.util.Map)return new java.util.TreeMap<>((java.util.Map)x).toString();if(x instanceof java.util.Set)return new java.util.TreeSet<>((java.util.Set)x).toString();return x.toString();}";
 public OjJavaSandbox(String image){docker=new DockerJavaSandbox(image,3,90);}
 public GradingResult grade(Exercise exercise,String source,List<Exercise.TestCase> tests,double maxScore,Map<String,Object> config)throws Exception{
  long start=System.nanoTime(),deadline=start+TimeUnit.MILLISECONDS.toNanos(DockerJavaSandbox.jobWallMillis()),executionRemaining=90000;int memory=integer(config,"memory_limit_mb",256),output=integer(config,"output_limit_kb",2048)*1024;
  String entry=exercise.entryFile();if(!entry.matches("[A-Za-z][A-Za-z0-9_]*\\.java"))throw new IllegalArgumentException("Invalid entry file");
  if(source==null||source.getBytes(StandardCharsets.UTF_8).length>32768)return result("COMPILE_ERROR",0,maxScore,0,tests.size(),"Source vượt 32 KiB.",start,List.of());
  if(!exercise.javaStructure().equals("PROGRAM")&&java.util.regex.Pattern.compile("\\b(?:class|interface|enum|record)\\s+(?:OJHarness_\\w*|java|javax|System|Class|ClassLoader|Runtime|String|Integer|Long|Double|Float|Boolean|Math|Arrays|Collections|Objects|List|Map|Set|TreeMap|TreeSet|ArrayList|HashMap|HashSet|Path|Files)\\b").matcher(source).find())return result("COMPILE_ERROR",0,maxScore,0,tests.size(),"Không được định nghĩa lại lớp hạ tầng hoặc lớp harness của Judge.",start,List.of());
  Path base=DockerJavaSandbox.createWorkspace("studyfix-oj-");List<Map<String,Object>> rows=new ArrayList<>();int passed=0;long earned=0,total=tests.stream().mapToLong(Exercise.TestCase::weight).sum();String verdict="ACCEPTED";
  try{
   Files.createDirectory(base.resolve("classes"));Files.writeString(base.resolve(entry),source,StandardCharsets.UTF_8);
   List<String> sources=new ArrayList<>(List.of("./"+entry));for(Object raw:list(config.get("support_files"))){var f=map(raw);String name=text(f,"path","");if(!name.matches("[A-Za-z][A-Za-z0-9_]*\\.java")||name.equals(entry))throw new IllegalArgumentException("Invalid support file");Files.writeString(base.resolve(name),text(f,"content",""),StandardCharsets.UTF_8);sources.add("./"+name);}
   var compiled=compile(base,sources,memory,deadline);if(compiled.exitCode()!=0||compiled.timedOut()||compiled.startFailure()!=null)return result(compileVerdict(compiled),0,maxScore,0,tests.size(),compiled.startFailure()!=null?safe(compiled.startFailure()):Boolean.FALSE.equals(config.get("show_compile"))?"Không biên dịch được code.":safe(compiled.stderrText()),start,List.of());
   for(var test:tests){if(System.nanoTime()>deadline)return result("SANDBOX_UNAVAILABLE",0,maxScore,passed,tests.size(),"Docker vượt giới hạn thời gian vận hành của lượt chấm.",start,rows);if(executionRemaining<=0){verdict="TIME_LIMIT_EXCEEDED";rows.add(row(test,false,"TIME_LIMIT_EXCEEDED",0));continue;}Path work=base;String main="Main";
    try{
     if(!exercise.javaStructure().equals("PROGRAM")){
      work=DockerJavaSandbox.createWorkspace("studyfix-oj-case-");Files.createDirectory(work.resolve("classes"));try(var paths=Files.walk(base.resolve("classes"))){for(Path file:paths.toList()){Path target=work.resolve("classes").resolve(base.resolve("classes").relativize(file));if(Files.isDirectory(file))Files.createDirectories(target);else Files.copy(file,target);}}
      main="OJHarness_"+UUID.randomUUID().toString().replace("-","");String harness="import java.util.*;import java.nio.file.*;public class "+main+"{"+FORMAT+"public static void main(String[] args)throws Exception{"+test.input()+"}}";Files.writeString(work.resolve(main+".java"),harness,StandardCharsets.UTF_8);
      var built=compile(work,List.of("./"+main+".java"),memory,deadline);if(built.exitCode()!=0||built.timedOut()||built.startFailure()!=null){verdict=compileVerdict(built).equals("SANDBOX_UNAVAILABLE")?"SANDBOX_UNAVAILABLE":"COMPILE_ERROR";rows.add(row(test,false,"Code chưa đúng class/constructor/method API bắt buộc trong đề bài.",built.elapsedMillis()));return result(verdict,0,maxScore,passed,tests.size(),"Code chưa đúng class/constructor/method API bắt buộc trong đề bài.",start,rows);}
     }
     byte[] input=exercise.javaStructure().equals("PROGRAM")?test.input().getBytes(StandardCharsets.UTF_8):null;List<String> args=new ArrayList<>(List.of("java","-Xmx"+Math.max(32,memory/2)+"m","-XX:ActiveProcessorCount=1","-cp","/code/classes",main));
     if("file_args0".equals(config.get("test_delivery"))){Files.writeString(work.resolve("input.fixture"),test.input(),StandardCharsets.UTF_8);args.add("/code/input.fixture");input=null;}
     String name=DockerJavaSandbox.containerName("oj-run");List<String> command=docker.dockerBase(name,work,true,memory);command.addAll(args);var run=docker.runBudgeted(command,input,Math.max(1,Math.min(integer(config,"time_limit_ms",3000),executionRemaining)),output,16*1024,name,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));executionRemaining-=run.elapsedMillis();
     String state=run.startFailure()!=null||run.exitCode()==125?"SANDBOX_UNAVAILABLE":run.timedOut()?"TIME_LIMIT_EXCEEDED":run.outputExceeded()?"OUTPUT_LIMIT_EXCEEDED":run.exitCode()==137||run.stderrText().contains("OutOfMemoryError")?"MEMORY_LIMIT_EXCEEDED":run.exitCode()!=0?"RUNTIME_ERROR":compare(run.stdoutText(),test.expected(),config)?"ACCEPTED":"WRONG_ANSWER";
     boolean ok=state.equals("ACCEPTED");if(ok){passed++;earned+=test.weight();}else if(verdict.equals("ACCEPTED")||verdict.equals("WRONG_ANSWER"))verdict=state;
     var detail=row(test,ok,ok?"Đạt":test.hidden()?"Hidden test không đạt":state+(!state.equals("WRONG_ANSWER")?"":": "+safe(run.stdoutText())),run.elapsedMillis());
     if(Boolean.FALSE.equals(config.get("show_public"))&&!test.hidden())detail.put("message",ok?"Đạt":"Test không đạt");
     if(Boolean.TRUE.equals(config.get("capture_output"))){detail.put("observedOutput",run.stdoutText());detail.put("executionOk",run.exitCode()==0&&!run.timedOut()&&!run.outputExceeded()&&run.startFailure()==null);}
     rows.add(detail);
    }finally{if(!work.equals(base))remove(work);}
   }
   double score=total==0?0:Math.round(maxScore*earned/total*10000d)/10000d;if(Boolean.FALSE.equals(config.get("partial_scoring"))&&passed!=tests.size())score=0;
   return result(passed==tests.size()?"ACCEPTED":verdict,score,maxScore,passed,tests.size(),"Đạt "+passed+"/"+tests.size()+" test. Nội dung test ẩn được giữ riêng trên server.",start,rows);
  }finally{remove(base);}
 }
 private DockerJavaSandbox.ProcessResult compile(Path work,List<String> sources,int memory,long deadline){String name=DockerJavaSandbox.containerName("oj-compile");List<String> command=docker.dockerBase(name,work,false,memory);command.addAll(List.of("javac","-J-Xmx"+Math.max(32,memory/2)+"m","-J-XX:ActiveProcessorCount=1","--release","17","-proc:none","-encoding","UTF-8","-cp","classes","-d","classes"));command.addAll(sources);return docker.runBudgeted(command,null,30000,12000,12000,name,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
 private static String compileVerdict(DockerJavaSandbox.ProcessResult r){return r.startFailure()!=null||r.exitCode()==125||r.exitCode()==126||r.exitCode()==127?"SANDBOX_UNAVAILABLE":r.timedOut()?"TIME_LIMIT_EXCEEDED":"COMPILE_ERROR";}
 private static String normalize(String value){return value.replace("\r\n","\n").replace('\r','\n');}
 public static boolean compare(String actual,String expected,Map<String,Object> config){
  String mode=text(config,"checker","EXACT");if(mode.equals("EXACT"))return normalize(actual).equals(normalize(expected));
  String[] a=actual.strip().isEmpty()?new String[0]:actual.strip().split("\\s+"),b=expected.strip().isEmpty()?new String[0]:expected.strip().split("\\s+");if(a.length!=b.length)return false;
  double epsilon=config.get("epsilon") instanceof Number n?n.doubleValue():0.000001;
  for(int i=0;i<a.length;i++){if(mode.equals("TOKEN")){if(!a[i].equals(b[i]))return false;continue;}try{double x=Double.parseDouble(a[i]),y=Double.parseDouble(b[i]);if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x-y)>epsilon*Math.max(1,Math.abs(y)))return false;}catch(NumberFormatException e){if(!a[i].equals(b[i]))return false;}}return true;
 }
 private static String safe(String text){return text==null?"":text.substring(0,Math.min(4000,text.length()));}
 private static Map<String,Object> row(Exercise.TestCase test,boolean passed,String message,long duration){return mapOf("hidden",test.hidden(),"passed",passed,"message",test.hidden()?(passed?"Hidden test đạt":"Hidden test không đạt"):message,"durationMs",duration);}
 private static GradingResult result(String v,double score,double max,int passed,int total,String message,long started,List<Map<String,Object>> rows){return new GradingResult(v,score,max,passed,total,message,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),rows);}
 private static void remove(Path path)throws Exception{DockerJavaSandbox.removeWorkspace(path);}
}
