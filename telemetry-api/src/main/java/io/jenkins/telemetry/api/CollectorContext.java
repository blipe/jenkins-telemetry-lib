package io.jenkins.telemetry.api;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

public record CollectorContext(
    String collectorId,
    Path workDirectory,
    Clock clock,
    Map<String, String> configuration
) {
    public CollectorContext {
        if (collectorId == null || collectorId.isBlank()) {
            throw new IllegalArgumentException("collectorId must not be blank");
        }
        if (workDirectory == null) {
            throw new IllegalArgumentException("workDirectory must not be null");
        }
        clock = clock == null ? Clock.systemUTC() : clock;
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }

    public String require(String key) {
        String value = configuration.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing collector configuration: " + key);
        }
        return value;
    }

    public String get(String key, String fallback) {
        return configuration.getOrDefault(key, fallback);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = configuration.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public int getInt(String key, int fallback) {
        String value = configuration.get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }
}
