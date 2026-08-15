package org.jenkinsci.plugins.workflow.cps;
import hudson.ExtensionPoint;
public abstract class GlobalVariable implements ExtensionPoint {
    public abstract String getName();
    public abstract Object getValue(CpsScript script) throws Exception;
}
