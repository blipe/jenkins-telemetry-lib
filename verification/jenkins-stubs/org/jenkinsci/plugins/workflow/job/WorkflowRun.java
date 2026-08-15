package org.jenkinsci.plugins.workflow.job;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
public class WorkflowRun extends Run<WorkflowJob, WorkflowRun> {
    public FlowExecution getExecution() { return new FlowExecution(); }
}
