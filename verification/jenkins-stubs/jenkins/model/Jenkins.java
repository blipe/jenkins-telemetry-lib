package jenkins.model;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
public class Jenkins {
    private static final Jenkins INSTANCE = new Jenkins();
    public static Jenkins get() { return INSTANCE; }
    public File getRootDir() { return new File("."); }
    public String getRootUrl() { return "http://jenkins/"; }
    public <T> List<T> getExtensionList(Class<T> type) { return new ArrayList<>(); }
    public <T> T getItemByFullName(String name, Class<T> type) { return null; }
}
