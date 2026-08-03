package hudson.model;
public class Result {
    public static final Result SUCCESS = new Result();
    public static final Result ABORTED = new Result();
    public boolean isBetterOrEqualTo(Result other) { return true; }
    @Override public String toString() { return "SUCCESS"; }
}
