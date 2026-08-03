package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public final class TelemetryRunListener extends RunListener<Run<?,?>> {
    private static final String WORKFLOW_RUN="org.jenkinsci.plugins.workflow.job.WorkflowRun";
    @Override public void onStarted(Run<?,?> run,TaskListener listener){if(isPipeline(run))JenkinsTelemetryRuntime.started(run,listener);}
    @Override public void onCompleted(Run<?,?> run,TaskListener listener){if(isPipeline(run))JenkinsTelemetryRuntime.completed(run,listener);}
    private static boolean isPipeline(Run<?,?>run){return run!=null&&WORKFLOW_RUN.equals(run.getClass().getName());}
}
