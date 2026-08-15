package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

@Extension
public final class JenkinsTelemetryGlobalVariable extends GlobalVariable {
    @Override public String getName() { return "jenkinsTelemetry"; }
    @Override public Object getValue(CpsScript script) { return new JenkinsTelemetryDsl(script); }
}
