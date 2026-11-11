package studyfix;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.net.URI;
import java.util.*;

/** Parse only: code is never executed here; comments and strings are not mutation targets. */
final class DebugCodeMutator {
    record Edit(int start, int end, String before, String after, long line) { }
    record Variant(String code, List<Edit> edits) { }
    static List<Variant> variants(String code, String type, int count, long seed) throws Exception {
        if (!Set.of("CONDITION", "ARITHMETIC", "MIXED").contains(type) || count < 1 || count > 3)
            throw new IllegalArgumentException("Chọn nhóm lỗi hợp lệ và từ 1 đến 3 lỗi.");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Cần JDK có Java compiler để tạo lỗi.");
        List<Edit> edits = new ArrayList<>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Main.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return code; }
        };
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, List.of(file));
            for (CompilationUnitTree unit : task.parse()) {
                SourcePositions positions = Trees.instance(task).getSourcePositions();
                new TreeScanner<Void, Void>() {
                    @Override public Void visitBinary(BinaryTree tree, Void unused) {
                        String[] replacement = switch (tree.getKind()) {
                            case LESS_THAN -> new String[]{"<", "<="};
                            case LESS_THAN_EQUAL -> new String[]{"<=", "<"};
                            case GREATER_THAN -> new String[]{">", ">="};
                            case GREATER_THAN_EQUAL -> new String[]{">=", ">"};
                            case EQUAL_TO -> new String[]{"==", "!="};
                            case NOT_EQUAL_TO -> new String[]{"!=", "=="};
                            case CONDITIONAL_AND -> new String[]{"&&", "||"};
                            case CONDITIONAL_OR -> new String[]{"||", "&&"};
                            case PLUS -> new String[]{"+", "-", "*"};
                            case MINUS -> new String[]{"-", "+", "*"};
                            case MULTIPLY -> new String[]{"*", "+", "-"};
                            case DIVIDE -> new String[]{"/", "*", "+"};
                            case REMAINDER -> new String[]{"%", "/", "*"};
                            default -> null;
                        };
                        boolean arithmetic = Set.of(Tree.Kind.PLUS, Tree.Kind.MINUS, Tree.Kind.MULTIPLY, Tree.Kind.DIVIDE, Tree.Kind.REMAINDER).contains(tree.getKind());
                        if (replacement != null && (type.equals("MIXED") || arithmetic == type.equals("ARITHMETIC"))) {
                            long left = positions.getEndPosition(unit, tree.getLeftOperand());
                            long right = positions.getStartPosition(unit, tree.getRightOperand());
                            if (left >= 0 && right >= left && right <= code.length()) {
                                String gap = code.substring((int)left, (int)right);
                                // Skip operators surrounded by comments rather than editing comment text.
                                if (gap.strip().equals(replacement[0])) {
                                    int start = (int)left + gap.indexOf(replacement[0]);
                                    for(int option=1;option<replacement.length;option++)
                                        edits.add(new Edit(start, start + replacement[0].length(), replacement[0], replacement[option], unit.getLineMap().getLineNumber(start)));
                                }
                            }
                        }
                        return super.visitBinary(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR))
            throw new IllegalArgumentException("Code nguồn không đúng cú pháp Java.");
        Map<Integer,List<Edit>> byPosition=new LinkedHashMap<>();
        for(Edit edit:edits)byPosition.computeIfAbsent(edit.start(),unused->new ArrayList<>()).add(edit);
        List<List<Edit>> positions=new ArrayList<>(byPosition.values());
        if (positions.size() < count) throw new IllegalArgumentException("Không đủ vị trí phù hợp; hãy giảm số lỗi hoặc đổi nhóm lỗi.");
        List<Variant> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Random random = new Random(seed);
        // Enumerate every single-position option before sampling combinations.
        if(count==1) {
            Collections.shuffle(edits,random);
            for(Edit edit:edits){out.add(new Variant(code.substring(0,edit.start())+edit.after()+code.substring(edit.end()),List.of(edit)));if(out.size()==7)break;}
            return out;
        }
        for (int attempt = 0; attempt < 80 && out.size() < 7; attempt++) {
            Collections.shuffle(positions, random);
            List<Edit> selected = positions.stream().limit(count).map(options->options.get(random.nextInt(options.size()))).sorted(Comparator.comparingInt(Edit::start).reversed()).toList();
            String changed = code;
            for (Edit edit : selected) changed = changed.substring(0, edit.start()) + edit.after() + changed.substring(edit.end());
            if (seen.add(changed)) out.add(new Variant(changed, selected));
        }
        return out;
    }
}