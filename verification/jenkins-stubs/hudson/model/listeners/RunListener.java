package hudson.model.listeners;
import hudson.model.*;
public class RunListener<R extends Run<?,?>> {
    public void onStarted(R run, TaskListener listener) {}
    public void onCompleted(R run, TaskListener listener) {}
}
