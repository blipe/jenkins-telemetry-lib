package org.jenkinsci.plugins.workflow.steps;
import java.util.Set;
public abstract class StepDescriptor {
    public abstract String getFunctionName();
    public abstract String getDisplayName();
    public Set<? extends Class<?>> getRequiredContext() { return Set.of(); }
}
