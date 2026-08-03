package io.jenkins.telemetry.core;

record JournalCursor(long sequence, long offset) {
    static final JournalCursor ZERO = new JournalCursor(0, 0);
}
