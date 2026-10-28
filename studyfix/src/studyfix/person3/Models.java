package studyfix.person3;

import java.time.Instant;
import java.util.List;

/** Immutable DTOs for assignments, submissions, exams and ranking. */
public final class Models {
    private Models() { }

    public record TestCase(String stdin, String expectedStdout) { }

    public record Assignment(
            long id,
            String roomId,
            String title,
            String statement,
            int maxScore,
            String testsRaw,
            String status,
            String createdBy,
            Instant createdAt,
            String topic) {
        public List<TestCase> tests() {
            return studyfix.Person3Service.parseTests(testsRaw);
        }
    }

    public record CatalogItem(
            long id,
            String topic,
            String code,
            String title,
            String statement,
            int maxScore,
            String testsRaw,
            int testCount) { }

    public record Submission(
            long id,
            long assignmentId,
            String roomId,
            String userId,
            String displayName,
            String code,
            Instant submittedAt,
            String status,
            Integer score,
            int maxScore,
            String details) { }

    public record Exam(
            long id,
            String roomId,
            String title,
            Instant startsAt,
            Instant endsAt,
            String status,
            String createdBy) { }

    public record RankEntry(int rank, String userId, String displayName, int totalScore, int submissions) { }

    public record RoomOverview(
            List<Assignment> assignments,
            List<Submission> submissions,
            Exam activeExam,
            List<RankEntry> ranking) { }
}
