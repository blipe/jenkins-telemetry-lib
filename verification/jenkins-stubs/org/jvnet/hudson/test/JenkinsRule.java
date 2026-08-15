package org.jvnet.hudson.test;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
public class JenkinsRule {
    public final Jenkins jenkins = Jenkins.get();
    public <T> T createProject(Class<T> type, String name) throws Exception { return type.getDeclaredConstructor().newInstance(); }
    public WorkflowRun buildAndAssertSuccess(WorkflowJob job) throws Exception { return null; }
    public void waitForMessage(String text, WorkflowRun run) throws Exception {}
    public WorkflowRun waitForCompletion(WorkflowRun run) throws Exception { return run; }
    public void assertBuildStatus(Result result, Run<?,?> run) {}
}
