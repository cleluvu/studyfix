public class Student {
    public final String name;
    public final double score;

    public Student(String name, double score) {
        this.name = name;
        this.score = score;
    }

    public String getName() { return name; }
    public double getScore() { return score; }
    public boolean isPassed() { return score >= 5; }
}
