public class Student {
    private final String name;
    private final double score;

    public Student(String name, double score) {
        this.name = name;
        this.score = score;
    }

    public String getName() { return name; }
    public double getScore() { return score; }
    public boolean isPassed() { return score >= 5; }
}
