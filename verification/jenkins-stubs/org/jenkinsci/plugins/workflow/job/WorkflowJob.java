package org.jenkinsci.plugins.workflow.job;
import hudson.model.Job;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
public class WorkflowJob extends Job<WorkflowJob, WorkflowRun> {
    public void setDefinition(CpsFlowDefinition definition) {}
    public QueueTaskFuture<WorkflowRun> scheduleBuild2(int quietPeriod) { return new QueueTaskFuture<>(); }
    public WorkflowRun getBuildByNumber(int number) { return null; }
}
