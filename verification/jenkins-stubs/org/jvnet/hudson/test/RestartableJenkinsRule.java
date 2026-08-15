package org.jvnet.hudson.test;
public class RestartableJenkinsRule {
    @FunctionalInterface public interface Step { void run(JenkinsRule rule) throws Throwable; }
    public void then(Step step) {}
}
