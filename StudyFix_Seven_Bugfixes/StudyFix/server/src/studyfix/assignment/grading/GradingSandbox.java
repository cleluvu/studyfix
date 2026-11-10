package studyfix.assignment.grading;
import studyfix.assignment.model.Exercise;
import studyfix.assignment.model.GradingResult;
import java.util.List;
public interface GradingSandbox {
 GradingResult grade(String submissionId,String source,List<Exercise.TestCase> tests,double maxScore) throws Exception;
}
