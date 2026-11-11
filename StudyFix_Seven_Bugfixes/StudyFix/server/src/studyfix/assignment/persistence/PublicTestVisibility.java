package studyfix.assignment.persistence;

import java.util.*;

/** Student-facing test details; hidden tests are never included. */
public final class PublicTestVisibility {
    private PublicTestVisibility() {}

    public static String mode(Map<String,Object> policy) {
        Object configured=policy.get("publicTestVisibility");
        if(configured!=null)return String.valueOf(configured);
        return Boolean.FALSE.equals(policy.get("showPublicTests"))?"NONE":"ALL";
    }

    public static List<Map<String,Object>> filter(List<Map<String,Object>> tests,Map<String,Object> policy) {
        String mode=mode(policy);
        return tests.stream().filter(t->Boolean.FALSE.equals(t.get("hidden")))
            .filter(t->switch(mode){case "PASSED"->Boolean.TRUE.equals(t.get("passed"));case "FAILED"->Boolean.FALSE.equals(t.get("passed"));case "ALL"->true;default->false;}).toList();
    }

    public static void apply(Map<String,Object> row,Map<String,Object> policy,List<Map<String,Object>> tests,boolean released) {
        String mode=mode(policy);boolean hidden=!released||mode.equals("NONE");
        Object report=row.get("structureResults");
        if(hidden)row.put("structureResults",List.of());else if(report instanceof List<?> values)row.put("structureResults",values.stream().filter(v->v instanceof Map<?,?> m&&(mode.equals("ALL")||mode.equals("PASSED")&&Boolean.TRUE.equals(m.get("passed"))||mode.equals("FAILED")&&Boolean.FALSE.equals(m.get("passed")))).toList());
        row.put("testsHidden",hidden);
        row.put("testResults",hidden?List.of():filter(tests,policy));
        // Aggregate counts/timings and judge feedback include hidden tests.
        row.put("passedTests",null);row.put("totalTests",null);row.put("durationMs",null);
        if(released)row.put("feedback",hidden?"Chi tiết kiểm thử được ẩn theo quy chế kỳ thi.":"Chỉ hiển thị test công khai theo quy chế kỳ thi.");
    }
}
