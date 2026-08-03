package io.jenkins.telemetry.core;

import java.io.IOException;

public final class JournalCapacityException extends IOException {
    public JournalCapacityException(String message) {
        super(message);
    }
}
