package studyfix.assignment.service;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;
public final class ProblemBuilderChecks {
 private ProblemBuilderChecks(){}
 public static List<String> step(Map<String,Object> data,int step){List<String> errors=new ArrayList<>();
  if(step==1){for(String key:List.of("title","code","learningOutcome","summary"))if(text(data,key,"").isBlank())errors.add("Chưa nhập "+key);if(!Set.of("Dễ","Trung bình","Khó").contains(text(data,"difficulty","")))errors.add("Chọn độ khó hợp lệ");Object minutes=data.get("estimatedMinutes");if(!(minutes instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>600)errors.add("Thời gian dự kiến phải là số nguyên 1–600 phút");if(!Set.of("PROGRAM","METHOD","CLASS").contains(text(data,"javaStructure","")))errors.add("Cấu trúc Java không hợp lệ");}
  if(step==2){Map<String,Object> statement=map(data.get("statement"));for(String key:List.of("description","inputFormat","outputFormat","constraints"))if(text(statement,key,"").isBlank())errors.add("Chưa nhập "+key);}
  errors.addAll(BuilderProblem.errors(data,step));return errors;
 }
}
