package studyfix.assignment.persistence;

import java.security.SecureRandom;
import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Randomized matching fills every source quota while excluding duplicate question roots. */
final class ExamCompositionSelector {
 static List<Map<String,Object>> select(List<List<Map<String,Object>>> pools,List<Integer> counts){
  List<List<Map<String,Object>>> slots=new ArrayList<>();SecureRandom random=new SecureRandom();
  for(int s=0;s<pools.size();s++){
   List<Map<String,Object>> pool=new ArrayList<>(pools.get(s));Collections.shuffle(pool,random);
   if(counts.get(s)>pool.size())throw new IllegalArgumentException("Một đề không đủ số câu khác nhau theo yêu cầu.");
   for(int n=0;n<counts.get(s);n++)slots.add(pool);
  }
  Map<String,Integer> owners=new HashMap<>();Map<Integer,Map<String,Object>> chosen=new HashMap<>();
  for(int slot=0;slot<slots.size();slot++)if(!assign(slot,slots,owners,chosen,new HashSet<>()))throw new IllegalArgumentException("Các đề có câu trùng nhau nên không đủ câu khác nhau để lấy đúng số lượng. Hãy giảm số câu hoặc chọn thêm đề.");
  List<Map<String,Object>> result=new ArrayList<>();for(int slot=0;slot<slots.size();slot++)result.add(chosen.get(slot));return result;
 }
 private static boolean assign(int slot,List<List<Map<String,Object>>> slots,Map<String,Integer> owners,Map<Integer,Map<String,Object>> chosen,Set<String> visited){
  for(var item:slots.get(slot)){String root=text(item,"rootId",text(item,"exerciseId",""));if(!visited.add(root))continue;Integer previous=owners.get(root);if(previous==null||assign(previous,slots,owners,chosen,visited)){owners.put(root,slot);chosen.put(slot,item);return true;}}
  return false;
 }
}
