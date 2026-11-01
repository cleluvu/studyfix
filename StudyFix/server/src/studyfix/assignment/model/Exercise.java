package studyfix.assignment.model;

import java.util.*;

public record Exercise(String id, String title, String topic, String difficulty,
                       String statement, String inputFormat, String outputFormat,
                       String constraints, String template, String hints,
                       String referenceSolution, ExerciseMode mode, String starterCode,
                       List<TestCase> tests) {
    public Exercise {
        Objects.requireNonNull(mode, "mode");
        starterCode = starterCode == null ? "" : starterCode;
        tests = List.copyOf(tests);
    }

    /** Compatibility constructor: existing catalog entries remain build-from-scratch exercises. */
    public Exercise(String id, String title, String topic, String difficulty,
                    String statement, String inputFormat, String outputFormat,
                    String constraints, String template, String hints,
                    String referenceSolution, List<TestCase> tests) {
        this(id, title, topic, difficulty, statement, inputFormat, outputFormat,
                constraints, template, hints, referenceSolution,
                ExerciseMode.BUILD_FROM_SCRATCH, template, tests);
    }

    public record TestCase(String input, String expected, int weight, boolean hidden) {}

    /** Public-safe view. Never include the reference solution or hidden expected outputs. */
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
        if (mode == ExerciseMode.DEBUG_COLLAB) view.put("starterCode", starterCode);
        return Collections.unmodifiableMap(view);
    }
}
