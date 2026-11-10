package studyfix.assignment.model;
import java.util.*;
public record GradingResult(String verdict,double score,double maxScore,int passed,int total,String feedback,long durationMillis,List<Map<String,Object>> publicTests) {
 public GradingResult { publicTests=List.copyOf(publicTests); }
}
