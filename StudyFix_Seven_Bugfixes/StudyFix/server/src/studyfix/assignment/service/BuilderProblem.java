package studyfix.assignment.service;

import studyfix.assignment.model.*;
import studyfix.assignment.persistence.*;
import java.util.*;
import java.math.BigDecimal;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** The authoring contract mapped to the same immutable OJ contract used by submissions. */
public final class BuilderProblem {
 private BuilderProblem(){}
 public static List<Map<String,Object>> files(Map<String,Object> p){
  Map<String,Object> t=map(p.get("template"));List<Map<String,Object>> files=new ArrayList<>();
  for(Object f:list(t.get("files")))files.add(map(f));
  if(files.isEmpty())files.add(mapOf("path",text(t,"entryFile",text(t,"mainClass","Main")+".java"),"content",text(t,"source",""),"visibility","PUBLIC","editable",true));
  return files;
 }
 public static String entry(Map<String,Object> p){var t=map(p.get("template"));return text(t,"entryFile",text(t,"mainClass","Main")+".java");}
 public static String source(Map<String,Object> p){return files(p).stream().filter(f->entry(p).equals(f.get("path"))).map(f->text(f,"content","")).findFirst().orElse("");}
 public static double number(Map<String,Object> m,String key,double fallback){Object v=m.get(key);return v instanceof Number n?n.doubleValue():fallback;}
 public static Map<String,Object> config(Map<String,Object> p){
  var g=map(p.get("grading"));List<Map<String,Object>> support=new ArrayList<>();
  for(var f:files(p))if(!entry(p).equals(f.get("path"))&&text(f,"path","").endsWith(".java")&&"PUBLIC".equals(text(f,"visibility","PUBLIC")))support.add(mapOf("path",f.get("path"),"content",f.get("content")));
  return mapOf("java_version",17,"time_limit_ms",integer(g,"timeLimitMs",3000),"memory_limit_mb",integer(g,"memoryLimitMb",256),"output_limit_kb",integer(g,"outputLimitKb",2048),"checker",text(g,"checker","EXACT"),"epsilon",number(g,"epsilon",0.000001),"partial_scoring",!Boolean.FALSE.equals(g.get("partialScoring")),"show_compile",!Boolean.FALSE.equals(g.get("showCompile")),"show_runtime",!Boolean.FALSE.equals(g.get("showRuntime")),"show_public",!Boolean.FALSE.equals(g.get("showPublic")),"test_delivery",text(g,"testDelivery","stdin"),"support_files",support,"structure_rules",studyfix.assignment.grading.StructureRules.normalize(g.get("structure")));
 }
 public static Exercise exercise(Map<String,Object> p,String id){
  List<Exercise.TestCase> tests=new ArrayList<>();
  for(Object raw:list(p.get("tests"))){var t=map(raw);int weight=BigDecimal.valueOf(number(t,"weight",0)).movePointRight(6).intValueExact();tests.add(new Exercise.TestCase(text(t,"input",""),text(t,"expectedOutput",""),weight,Boolean.TRUE.equals(t.get("hidden"))));}
  var s=map(p.get("statement"));String statement=studyfix.assignment.grading.StructureRules.withoutGeneratedStatement(text(s,"description",""));for(var f:files(p))if(!entry(p).equals(f.get("path"))&&"PUBLIC".equals(f.get("visibility")))statement+="\n\n### File hỗ trợ: "+text(f,"path","")+"\n\n```\n"+text(f,"content","")+"\n```\n";for(Object raw:list(s.get("attachments"))){var a=map(raw);statement+="\n\n[Tài liệu đính kèm](/api/problem-builder/assets/"+text(a,"id","")+")\n";}return new Exercise(id,text(p,"title",""),text(p,"topic","Java"),text(p,"difficulty","Dễ"),statement+studyfix.assignment.grading.StructureRules.statement(map(p.get("grading")).get("structure")),text(s,"inputFormat",""),text(s,"outputFormat",""),text(s,"constraints",""),source(p),"",text(p,"reference",""),ExerciseMode.BUILD_FROM_SCRATCH,source(p),tests,text(p,"javaStructure","PROGRAM"),entry(p));
 }
 public static List<String> errors(Map<String,Object> p,int step){
  List<String> out=new ArrayList<>();var t=map(p.get("template"));var g=map(p.get("grading"));
  if(step==3){String entry=entry(p);if(!entry.matches("[A-Za-z][A-Za-z0-9_]*\\.java"))out.add("File nộp phải là tên Java ở thư mục gốc, ví dụ Solution.java.");if("PROGRAM".equals(text(p,"javaStructure","PROGRAM"))&&!entry.equals("Main.java"))out.add("Program dùng Main.java.");
   int editable=0;for(var f:files(p)){if(Boolean.TRUE.equals(f.get("editable")))editable++;if(text(f,"path","").endsWith(".java")&&"PUBLIC".equals(f.get("visibility"))){if(!text(f,"path","").matches("[A-Za-z][A-Za-z0-9_]*\\.java"))out.add("Java source chạy OJ phải ở thư mục gốc.");if(java.util.regex.Pattern.compile("(?m)^\\s*package\\s+").matcher(text(f,"content","")).find())out.add("Runner hiện dùng default package; bỏ khai báo package.");}}
   if(editable!=1||files(p).stream().noneMatch(f->entry.equals(f.get("path"))&&"PUBLIC".equals(f.get("visibility"))&&Boolean.TRUE.equals(f.get("editable"))))out.add("Chọn đúng một file Java công khai cho sinh viên sửa; tên phải trùng file nộp.");if(source(p).isBlank())out.add("Template file nộp chưa có nội dung.");if(source(p).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)out.add("File Java nộp tối đa 32 KiB UTF-8.");
  }
  if(step==4){List<Object> tests=list(p.get("tests"));if(tests.isEmpty()||tests.size()>100)out.add("Cần 1–100 testcase.");Set<String> ids=new HashSet<>(),cases=new HashSet<>();Set<String> groups=new HashSet<>();for(Object group:list(p.get("testGroups")))groups.add(String.valueOf(group));
   for(Object raw:tests){var test=map(raw);String id=text(test,"id","");if(!id.matches("[A-Za-z0-9_-]{1,40}")||!ids.add(id))out.add("Mã testcase thiếu, sai định dạng hoặc trùng: "+id);if(!(test.get("input") instanceof String)||!(test.get("expectedOutput") instanceof String))out.add(id+": phải khai báo input và expected (được phép rỗng).");double weight=number(test,"weight",-1);if(!Double.isFinite(weight)||weight<=0||weight>100||Math.abs(weight*1000000-Math.rint(weight*1000000))>0.001)out.add(id+": điểm phải dương, tối đa sáu chữ số thập phân.");if(!(test.get("hidden") instanceof Boolean))out.add(id+": thiếu Public/Hidden.");if(!groups.contains(text(test,"group","")))out.add(id+": nhóm test không tồn tại.");String signature=text(test,"input","")+"\u0000"+text(test,"expectedOutput","");if(!cases.add(signature))out.add(id+": testcase trùng dữ liệu/kết quả.");if(text(test,"input","").length()>16000||text(test,"expectedOutput","").length()>16000)out.add(id+": input/output vượt 16000 ký tự.");}
   if(!tests.isEmpty()&&tests.stream().noneMatch(x->!Boolean.TRUE.equals(map(x).get("hidden"))))out.add("Cần ít nhất một testcase Public.");
  }
  if(step==5){for(String key:List.of("javaVersion","timeLimitMs","memoryLimitMb","outputLimitKb"))if(g.containsKey(key)&&(!(g.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()))out.add(key+" phải là số nguyên.");if(!Set.of("EXACT","TOKEN","FLOAT").contains(text(g,"checker","")))out.add("Chọn checker Exact, Token hoặc Float.");if(integer(g,"javaVersion",17)!=17)out.add("Target Java được hỗ trợ là 17.");int time=integer(g,"timeLimitMs",0),memory=integer(g,"memoryLimitMb",0),output=integer(g,"outputLimitKb",0);if(time<100||time>10000)out.add("Time limit 100–10000 ms.");if(memory<64||memory>512)out.add("Memory limit 64–512 MiB.");if(output<1||output>2048)out.add("Output limit 1–2048 KiB.");double eps=number(g,"epsilon",0.000001);if(!Double.isFinite(eps)||eps<=0||eps>0.1)out.add("Sai số Float phải > 0 và ≤ 0.1.");if(!Set.of("stdin","file_args0").contains(text(g,"testDelivery","stdin")))out.add("Cách chuyển input không hợp lệ.");double total=0;for(Object raw:list(p.get("tests")))total+=number(map(raw),"weight",0);if(Math.abs(total-100)>0.00001)out.add("Tổng điểm testcase phải bằng 100; hiện tại "+total+".");for(String key:List.of("partialScoring","showCompile","showRuntime","showPublic"))if(g.containsKey(key)&&!(g.get(key) instanceof Boolean))out.add(key+" phải là boolean.");}
  return out;
 }
 public static void require(Map<String,Object> p,int through){List<String> errors=new ArrayList<>();for(int i=1;i<=through;i++)errors.addAll(ProblemBuilderChecks.step(p,i));if(!errors.isEmpty())throw new ProblemBuilderRepository.Failure("STEP_INVALID",String.join("; ",errors));}
}
