package org.jenkinsci.plugins.workflow.steps;
public abstract class StepContext implements java.io.Serializable {
    public abstract <T> T get(Class<T> type) throws java.io.IOException, InterruptedException;
}
