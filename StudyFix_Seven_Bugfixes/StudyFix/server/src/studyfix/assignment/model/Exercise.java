package studyfix.assignment.model;

import java.util.*;

public record Exercise(String id, String title, String topic, String difficulty,
                       String statement, String inputFormat, String outputFormat,
                       String constraints, String template, String hints,
                       String referenceSolution, ExerciseMode mode, String starterCode,
                       List<TestCase> tests, String javaStructure, String entryFile) {
    public Exercise {
        Objects.requireNonNull(mode, "mode");
        starterCode = starterCode == null ? "" : starterCode;
        tests = List.copyOf(tests);
    }
    public Exercise(String id,String title,String topic,String difficulty,String statement,String inputFormat,String outputFormat,String constraints,String template,String hints,String referenceSolution,ExerciseMode mode,String starterCode,List<TestCase> tests){this(id,title,topic,difficulty,statement,inputFormat,outputFormat,constraints,template,hints,referenceSolution,mode,starterCode,tests,"PROGRAM","Main.java");}

    /** Hàm tạo tương thích dữ liệu cũ: bài trong danh sách cũ vẫn được xem là bài lập trình từ đầu. */
    public Exercise(String id, String title, String topic, String difficulty,
                    String statement, String inputFormat, String outputFormat,
                    String constraints, String template, String hints,
                    String referenceSolution, List<TestCase> tests) {
        this(id, title, topic, difficulty, statement, inputFormat, outputFormat,
                constraints, template, hints, referenceSolution,
                ExerciseMode.BUILD_FROM_SCRATCH, template, tests);
    }

    public record TestCase(String input, String expected, int weight, boolean hidden) {}

    /** A specific explanation shared by the source picker and server-side validation. */
    public String debugPreparationIssue() {
        if("METHOD".equals(javaStructure))return "Chưa hỗ trợ bài Method ("+entryFile+")";
        if("CLASS".equals(javaStructure))return "Chưa hỗ trợ bài Class ("+entryFile+")";
        if(!"PROGRAM".equals(javaStructure)||!"Main.java".equals(entryFile))return "Chỉ hỗ trợ bài Program với Main.java";
        if(referenceSolution==null||referenceSolution.isBlank())return "Thiếu code chuẩn";
        if(tests.isEmpty())return "Chưa có testcase";
        if(referenceSolution.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32000)return "Code chuẩn vượt giới hạn 32000 byte";
        return "";
    }

    /** Dữ liệu được phép công khai, không chứa lời giải tham khảo hay đáp án test ẩn. */
    public Map<String, Object> publicView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("title", title);
        view.put("topic", topic);
        view.put("difficulty", difficulty);
        view.put("statement", statement);
        view.put("inputFormat", inputFormat);
        view.put("outputFormat", outputFormat);
        view.put("constraints", constraints);
        view.put("template", template);
        view.put("hints", hints);
        view.put("mode", mode.name());
        view.put("javaStructure",javaStructure);view.put("entryFile",entryFile);
        if (mode == ExerciseMode.DEBUG_COLLAB) view.put("starterCode", starterCode);
        return Collections.unmodifiableMap(view);
    }
}
