package studyfix.assignment.model;
import java.util.*;
public record GradingResult(String verdict,double score,double maxScore,int passed,int total,String feedback,long durationMillis,List<Map<String,Object>> publicTests,List<Map<String,Object>> structureResults) {
 public GradingResult(String verdict,double score,double maxScore,int passed,int total,String feedback,long durationMillis,List<Map<String,Object>> publicTests){this(verdict,score,maxScore,passed,total,feedback,durationMillis,publicTests,List.of());}
 public GradingResult { publicTests=List.copyOf(publicTests);structureResults=List.copyOf(structureResults); }
}
