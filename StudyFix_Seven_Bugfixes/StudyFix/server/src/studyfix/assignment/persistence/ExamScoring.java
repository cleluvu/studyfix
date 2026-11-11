package studyfix.assignment.persistence;

import java.util.Map;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Question weights converted to a ten-point result. */
public final class ExamScoring {
 public static final String EQUAL_TEN="EQUAL_TEN";
 public static final String WEIGHTED_TEN="WEIGHTED_TEN";
 public static final String RULE="EQUAL_WEIGHT_TEN_V1";
 private ExamScoring(){}
 public static boolean equalTen(Map<String,Object> exam){return java.util.Set.of(EQUAL_TEN,WEIGHTED_TEN).contains(text(exam,"scoringMode","WEIGHTED"));}
 public static double questionMax(Map<String,Object> exam,Map<String,Object> item,int assignedCount){
  if(equalTen(exam)){
   if(assignedCount<1)throw new IllegalArgumentException("Đề thi cần ít nhất một câu.");
   if(EQUAL_TEN.equals(text(exam,"scoringMode","")))return 10d/assignedCount;
   var items=list(exam.get("items"));double total=items.stream().mapToDouble(v->((Number)map(v).get("points")).doubleValue()).sum();
   if(!Double.isFinite(total)||total<=0)throw new IllegalArgumentException("Tổng điểm gốc phải lớn hơn 0.");
   if(assignedCount!=items.size()){
    double first=((Number)map(items.getFirst()).get("points")).doubleValue();
    if(items.stream().anyMatch(v->Math.abs(((Number)map(v).get("points")).doubleValue()-first)>1e-9))throw new IllegalArgumentException("Phát ngẫu nhiên yêu cầu các câu cùng trọng số.");
    total=first*assignedCount;
   }
   return 10d*((Number)item.get("points")).doubleValue()/total;
  }
  return ((Number)item.get("points")).doubleValue();
 }
 public static double result(double score){return Math.round(Math.max(0,Math.min(10,score))*100d)/100d;}
}
