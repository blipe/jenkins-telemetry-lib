package org.jenkinsci.plugins.workflow.steps;
public abstract class Step implements java.io.Serializable {
    public abstract StepExecution start(StepContext context) throws Exception;
}
