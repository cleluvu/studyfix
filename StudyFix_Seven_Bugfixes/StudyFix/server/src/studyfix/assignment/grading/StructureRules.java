package studyfix.assignment.grading;

import java.util.*;
import static studyfix.assignment.protocol.AssignmentJson.*;

/** Versioned, declarative contract. Never accepts executable checker code. */
public final class StructureRules {
 private StructureRules(){}
 public static String withoutGeneratedStatement(String statement){int marker=statement.indexOf("\n\n## Tiêu chí chấm cấu trúc OOP\n");return marker<0?statement:statement.substring(0,marker);}
 public static String statement(Object input){var rules=normalize(input);if(!Boolean.TRUE.equals(rules.get("enabled")))return "";StringBuilder text=new StringBuilder("\n\n## Tiêu chí chấm cấu trúc OOP\n\nĐiểm cấu trúc: "+rules.get("weight")+"%. "+(Boolean.TRUE.equals(rules.get("gate"))?"Phải đạt mọi tiêu chí mới chạy test hành vi.":"Chấm từng tiêu chí theo trọng số.")+"\n");for(Object raw:list(rules.get("rules"))){var r=map(raw);text.append("\n- `").append(r.get("id")).append("`: ").append(r.get("kind")).append(" ").append(r.get("owner"));if(!text(r,"name","").isEmpty())text.append('.').append(r.get("name"));if(Set.of("METHOD","CONSTRUCTOR","OVERRIDE","ANNOTATION").contains(r.get("kind")))text.append(r.get("parameters"));for(String key:List.of("type","visibility","target"))if(!text(r,key,"").isEmpty())text.append(" · ").append(key).append('=').append(r.get(key));for(String key:List.of("modifiers","forbiddenModifiers"))if(!list(r.get(key)).isEmpty())text.append(" · ").append(key).append('=').append(r.get(key));text.append(" · weight=").append(r.get("weight"));}return text.append('\n').toString();}
 public static Map<String,Object> normalize(Object input){
  if(input!=null&&!(input instanceof Map<?,?>))throw new IllegalArgumentException("Cấu hình cấu trúc phải là đối tượng.");
  Map<String,Object> raw=map(input);if(raw.isEmpty())return Map.of();
  keys(raw,Set.of("schemaVersion","enabled","weight","gate","rules"));
  if(integer(raw,"schemaVersion",1)!=1)throw new IllegalArgumentException("Phiên bản quy tắc cấu trúc không hỗ trợ.");
  for(String key:List.of("enabled","gate"))if(raw.containsKey(key)&&!(raw.get(key) instanceof Boolean))throw new IllegalArgumentException(key+" phải là boolean.");
  double share=number(raw.getOrDefault("weight",30));if(share<1||share>100)throw new IllegalArgumentException("Điểm cấu trúc phải từ 1 đến 100%.");
  if(!(raw.getOrDefault("rules",List.of()) instanceof List<?>))throw new IllegalArgumentException("rules phải là danh sách.");
  List<Map<String,Object>> rules=new ArrayList<>();Set<String> ids=new HashSet<>();
  for(Object value:list(raw.get("rules"))){if(!(value instanceof Map))throw new IllegalArgumentException("Quy tắc phải là đối tượng.");var r=map(value);keys(r,Set.of("id","kind","owner","name","type","parameters","visibility","modifiers","forbiddenModifiers","target","direct","declared","weight"));
   String id=text(r,"id",""),kind=text(r,"kind","");if(!id.matches("[A-Za-z0-9_.-]{1,80}")||!ids.add(id))throw new IllegalArgumentException("Mã quy tắc thiếu hoặc trùng.");
   if(!Set.of("TYPE","FIELD","CONSTRUCTOR","METHOD","EXTENDS","IMPLEMENTS","OVERRIDE","ANNOTATION").contains(kind))throw new IllegalArgumentException("Loại quy tắc không hỗ trợ: "+kind);
   String owner=identifier(r,"owner",true),name=identifier(r,"name",Set.of("FIELD","METHOD","OVERRIDE","ANNOTATION").contains(kind)),target=type(r,"target",Set.of("EXTENDS","IMPLEMENTS","OVERRIDE","ANNOTATION").contains(kind));
   String visibility=text(r,"visibility","");if(!Set.of("","PUBLIC","PROTECTED","PRIVATE","PACKAGE").contains(visibility))throw new IllegalArgumentException("Quyền truy cập không hợp lệ.");
   List<String> modifiers=flags(r,"modifiers"),forbidden=flags(r,"forbiddenModifiers");if(modifiers.stream().anyMatch(forbidden::contains))throw new IllegalArgumentException("Modifier vừa bắt buộc vừa bị cấm.");
   List<String> parameters=new ArrayList<>();if(r.containsKey("parameters")&&!(r.get("parameters") instanceof List<?>))throw new IllegalArgumentException("parameters phải là danh sách kiểu.");for(Object p:list(r.get("parameters"))){String t=String.valueOf(p);validateType(t);parameters.add(t);}if(parameters.size()>20)throw new IllegalArgumentException("Quá nhiều tham số.");
   String expected=type(r,"type",false);if(kind.equals("TYPE")&&!Set.of("CLASS","INTERFACE","ENUM","RECORD","ANNOTATION").contains(expected))throw new IllegalArgumentException("TYPE cần type CLASS, INTERFACE, ENUM, RECORD hoặc ANNOTATION.");
   double weight=number(r.getOrDefault("weight",1));if(weight<=0||weight>100)throw new IllegalArgumentException("Trọng số quy tắc phải > 0 và ≤ 100.");
   for(String key:List.of("direct","declared"))if(r.containsKey(key)&&!(r.get(key) instanceof Boolean))throw new IllegalArgumentException(key+" phải là boolean.");
   rules.add(mapOf("id",id,"kind",kind,"owner",owner,"name",name,"target",target,"type",expected,"parameters",parameters,"visibility",visibility,"modifiers",modifiers,"forbiddenModifiers",forbidden,"direct",!Boolean.FALSE.equals(r.get("direct")),"declared",!Boolean.FALSE.equals(r.get("declared")),"weight",weight));
  }
  if(rules.size()>100||Boolean.TRUE.equals(raw.get("enabled"))&&rules.isEmpty())throw new IllegalArgumentException("Cần 1–100 quy tắc khi bật chấm cấu trúc.");
  return mapOf("schemaVersion",1,"enabled",Boolean.TRUE.equals(raw.get("enabled")),"weight",share,"gate",Boolean.TRUE.equals(raw.get("gate")),"rules",rules);
 }
 private static void keys(Map<String,Object> m,Set<String> allowed){for(String key:m.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Trường cấu trúc không hỗ trợ: "+key);}
 private static double number(Object value){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Trọng số phải là số hữu hạn.");return n.doubleValue();}
 private static String identifier(Map<String,Object> r,String key,boolean required){String s=text(r,key,"");if((required||!s.isEmpty())&&!s.matches("[A-Za-z_$][A-Za-z0-9_$.]{0,159}"))throw new IllegalArgumentException("Tên "+key+" không hợp lệ.");return s;}
 private static String type(Map<String,Object> r,String key,boolean required){String s=text(r,key,"");if(required&&s.isEmpty())throw new IllegalArgumentException("Thiếu "+key);if(!s.isEmpty())validateType(s);return s;}
 private static void validateType(String s){if(s.isBlank()||s.length()>240||!s.matches("[A-Za-z0-9_$.<>?, \\[\\]]+"))throw new IllegalArgumentException("Tên kiểu không hợp lệ: "+s);}
 private static List<String> flags(Map<String,Object> r,String key){if(r.containsKey(key)&&!(r.get(key) instanceof List<?>))throw new IllegalArgumentException(key+" phải là danh sách.");List<String> values=list(r.get(key)).stream().map(String::valueOf).toList();if(values.size()>8||values.stream().anyMatch(v->!Set.of("PUBLIC","PROTECTED","PRIVATE","STATIC","FINAL","ABSTRACT","SYNCHRONIZED","NATIVE","TRANSIENT","VOLATILE","DEFAULT","STRICTFP","SEALED","NON_SEALED").contains(v)))throw new IllegalArgumentException("Modifier không hỗ trợ.");return values;}
}
