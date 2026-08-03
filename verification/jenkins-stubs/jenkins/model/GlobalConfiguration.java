package jenkins.model;
import hudson.ExtensionPoint;
public abstract class GlobalConfiguration implements ExtensionPoint {
    protected void load() {}
    public void save() {}
}
