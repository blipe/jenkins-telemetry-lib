package io.jenkins.telemetry.jenkins;

import io.jenkins.telemetry.api.Attributes;
import io.jenkins.telemetry.api.Correlation;
import io.jenkins.telemetry.core.TelemetryEngine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class ConsoleCaptureOutputStream extends OutputStream {
    private static final Pattern ANSI = Pattern.compile("\\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");
    private final TelemetryEngine engine;
    private final TelemetryRunAction action;
    private final Correlation correlation;
    private final int maxLineCharacters;
    private final ByteArrayOutputStream current = new ByteArrayOutputStream();
    private boolean truncated;

    ConsoleCaptureOutputStream(TelemetryEngine engine, TelemetryRunAction action, Correlation correlation, int maxLineCharacters) {
        this.engine = engine; this.action = action; this.correlation = correlation;
        this.maxLineCharacters = Math.max(1024, maxLineCharacters);
        String remainder = action.consoleRemainder();
        if (!remainder.isEmpty()) current.writeBytes(remainder.getBytes(StandardCharsets.UTF_8));
    }
    @Override public void write(int value) throws IOException {
        if (value == '\n') { emit(); return; }
        if (current.size() < maxLineCharacters * 4) current.write(value); else truncated = true;
    }
    @Override public void write(byte[] bytes, int offset, int length) throws IOException {
        for (int i = offset; i < offset + length; i++) write(bytes[i] & 0xff);
    }
    void finish(boolean completed) {
        if (completed) { if (current.size() > 0 || truncated) emit(); action.consoleRemainder(""); }
        else action.consoleRemainder(current.toString(StandardCharsets.UTF_8));
    }
    private void emit() {
        String line = current.toString(StandardCharsets.UTF_8); current.reset();
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        line = ANSI.matcher(line).replaceAll("");
        if (line.length() > maxLineCharacters) { line = line.substring(0, maxLineCharacters); truncated = true; }
        Attributes attributes = Attributes.builder().put("log.source", "jenkins.console")
            .put("log.sequence", action.nextConsoleSequence()).put("log.truncated", truncated).build();
        engine.recordConsoleLog(line, attributes, correlation); truncated = false;
    }
}
