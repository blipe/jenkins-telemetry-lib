package hudson.model;
public class Job<P extends Job<P,R>, R extends Run<P,R>> {
    public String getFullName() { return ""; }
}
