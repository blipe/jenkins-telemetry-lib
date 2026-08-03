package hudson.model;
import hudson.console.AnnotatedLargeText;
import java.io.*;
public class Run<P extends Job<P,R>, R extends Run<P,R>> {
    public String getExternalizableId() { return ""; }
    public <T extends Action> T getAction(Class<T> type) { return null; }
    public void addAction(Action action) {}
    public File getRootDir() { return new File("."); }
    public P getParent() { return null; }
    public int getNumber() { return 0; }
    public String getUrl() { return ""; }
    public Result getResult() { return Result.SUCCESS; }
    public AnnotatedLargeText<Run<P,R>> getLogText() { return new AnnotatedLargeText<>(); }
    public java.util.List<String> getLog(int maxLines) { return java.util.List.of(); }
    public void save() throws IOException {}
}
