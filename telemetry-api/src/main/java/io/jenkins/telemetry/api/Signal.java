package io.jenkins.telemetry.api;

public enum Signal {
    LOGS("logs"),
    METRICS("metrics"),
    TRACES("traces");

    private final String wireName;

    Signal(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Signal parse(String value) {
        for (Signal signal : values()) {
            if (signal.name().equalsIgnoreCase(value) || signal.wireName.equalsIgnoreCase(value)) {
                return signal;
            }
        }
        throw new IllegalArgumentException("Unknown signal: " + value);
    }
}
