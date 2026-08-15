package io.jenkins.telemetry.api;

public interface Counter {
    void add(long delta);

    void add(long delta, Attributes attributes);
}
