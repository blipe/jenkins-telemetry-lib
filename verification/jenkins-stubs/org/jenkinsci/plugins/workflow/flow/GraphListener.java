package org.jenkinsci.plugins.workflow.flow;
import hudson.ExtensionPoint;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
public interface GraphListener extends ExtensionPoint {
    void onNewHead(FlowNode node);
    interface Synchronous extends GraphListener {}
}
