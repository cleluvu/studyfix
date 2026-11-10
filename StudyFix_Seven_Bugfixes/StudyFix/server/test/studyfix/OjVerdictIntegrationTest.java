package studyfix;
import studyfix.assignment.catalog.OjPackage;
import studyfix.assignment.grading.OjJavaSandbox;
import java.nio.file.*;
import java.util.*;
public final class OjVerdictIntegrationTest {
 public static void main(String[] args)throws Exception{
  var pack=new OjPackage(Files.readAllBytes(Path.of("private/catalogs/CodeBank_Java_100_OJ_Complete.zip")));var problem=pack.problems.stream().filter(p->p.code().equals("JAVA001")).findFirst().orElseThrow();var exercise=problem.exercise();var tests=exercise.tests().stream().filter(t->!t.hidden()).toList();var sandbox=new OjJavaSandbox("eclipse-temurin:21-jdk");
  Map<String,String> sources=new LinkedHashMap<>();sources.put("COMPILE_ERROR","public class Main { broken syntax }");sources.put("WRONG_ANSWER","public class Main { public static void main(String[] a){System.out.println(\"WRONG\");} }");sources.put("RUNTIME_ERROR","public class Main { public static void main(String[] a){throw new IllegalStateException();} }");sources.put("TIME_LIMIT_EXCEEDED","public class Main { public static void main(String[] a){while(true){}} }");sources.put("MEMORY_LIMIT_EXCEEDED","public class Main { public static void main(String[] a){java.util.List<byte[]> keep=new java.util.ArrayList<>();while(true)keep.add(new byte[10000000]);} }");
  var config=new LinkedHashMap<String,Object>(problem.config());if(System.getProperty("studyfix.test.docker.timeLimitMs")!=null)config.put("time_limit_ms",Integer.getInteger("studyfix.test.docker.timeLimitMs"));
  for(var sample:sources.entrySet()){var result=sandbox.grade(exercise,sample.getValue(),tests,100,config);if(!sample.getKey().equals(result.verdict()))throw new AssertionError("Expected "+sample.getKey()+", got "+result.verdict());System.out.println("Docker verdict confirmed: "+result.verdict());}
  System.out.println("OjVerdictIntegrationTest PASS: real Docker CE/WA/RE/TLE/MLE.");
 }
}
