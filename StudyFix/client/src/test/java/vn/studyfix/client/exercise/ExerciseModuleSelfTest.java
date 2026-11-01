package vn.studyfix.client.exercise;

import vn.studyfix.client.exercise.protocol.AssignmentJson;
import java.util.*;

/** Fast protocol test for the new Member-3 client module; no server socket required. */
public final class ExerciseModuleSelfTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String code = "public class Main {\n  public static void main(String[] args) {\n    System.out.println(\"Xin chào\");\n  }\n}\n";
        Map<String,Object> request = AssignmentJson.mapOf(
                "protocolVersion", 1,
                "requestId", "self-test-1",
                "type", "SUBMIT_CREATE",
                "submissionMode", "PERSONAL",
                "sourceCode", code,
                "hidden", false,
                "sampleInputs", List.of("1\n", "2 3\n"));
        Map<String,Object> decoded = AssignmentJson.object(AssignmentJson.stringify(request));
        check("SUBMIT_CREATE".equals(decoded.get("type")), "submission request type round trips");
        check(code.equals(decoded.get("sourceCode")), "Java source with Unicode/newlines round trips");
        check("PERSONAL".equals(decoded.get("submissionMode")), "personal submission mode round trips");
        check(AssignmentJson.list(decoded.get("sampleInputs")).size() == 2, "list payload round trips");
        boolean malformedRejected = false;
        try { AssignmentJson.object("{\"broken\":"); } catch (IllegalArgumentException expected) { malformedRejected = true; }
        check(malformedRejected, "malformed JSON is rejected");
        System.out.println("ExerciseModuleSelfTest PASS: " + checks + " checks");
    }
}
