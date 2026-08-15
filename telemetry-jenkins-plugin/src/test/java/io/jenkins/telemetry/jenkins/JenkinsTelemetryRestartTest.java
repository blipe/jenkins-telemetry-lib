package io.jenkins.telemetry.jenkins;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import static org.junit.Assert.*;

public class JenkinsTelemetryRestartTest {
    @Rule public RestartableJenkinsRule restartable=new RestartableJenkinsRule();
    @Test public void suspendedOperationSurvivesControllerRestart(){
        restartable.then(jenkins->{WorkflowJob job=jenkins.createProject(WorkflowJob.class,"restart-telemetry");job.setDefinition(new CpsFlowDefinition("jenkinsTelemetry.release(serviceName:'restart-demo',serviceVersion:'1')\njenkinsTelemetry.observe(name:'Long operation',kind:'VERIFY'){op->op.log('before restart');sleep time:1,unit:'HOURS'}",true));WorkflowRun run=job.scheduleBuild2(0).waitForStart();jenkins.waitForMessage("Sleeping for",run);TelemetryRunAction action=run.getAction(TelemetryRunAction.class);assertNotNull(action);assertEquals(1,action.openOperations().size());});
        restartable.then(jenkins->{WorkflowJob job=jenkins.jenkins.getItemByFullName("restart-telemetry",WorkflowJob.class);WorkflowRun run=job.getBuildByNumber(1);assertNotNull(run);TelemetryRunAction action=run.getAction(TelemetryRunAction.class);assertNotNull(action);assertEquals(1,action.openOperations().size());run.getExecution().interrupt(Result.ABORTED);jenkins.waitForCompletion(run);jenkins.assertBuildStatus(Result.ABORTED,run);});
    }
}
