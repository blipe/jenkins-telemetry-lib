package org.jenkinsci.plugins.workflow.flow;
public class FlowExecution {
 public FlowExecutionOwner getOwner() { return new FlowExecutionOwner(); }
 public void interrupt(hudson.model.Result result) {}
}
