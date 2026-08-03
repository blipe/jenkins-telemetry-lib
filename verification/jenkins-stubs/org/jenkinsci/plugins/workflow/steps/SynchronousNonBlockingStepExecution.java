package org.jenkinsci.plugins.workflow.steps;
public abstract class SynchronousNonBlockingStepExecution<T> extends StepExecution {
    private final StepContext context;
    protected SynchronousNonBlockingStepExecution(StepContext context) { this.context = context; }
    protected StepContext getContext() { return context; }
    protected abstract T run() throws Exception;
}
