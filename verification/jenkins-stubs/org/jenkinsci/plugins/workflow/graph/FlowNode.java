package org.jenkinsci.plugins.workflow.graph;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
public class FlowNode {
    public FlowExecution getExecution() { return new FlowExecution(); }
    public String getId() { return ""; }
    public String getDisplayName() { return ""; }
    public <T> T getAction(Class<T> type) { return null; }
    public ErrorAction getError() { return null; }
}
