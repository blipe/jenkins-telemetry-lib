package io.jenkins.telemetry.core;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

final class IdGenerator {
    private final SecureRandom random = new SecureRandom();

    String traceId() {
        byte[] bytes = new byte[16];
        do {
            random.nextBytes(bytes);
        } while (allZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    String spanId() {
        byte[] bytes = new byte[8];
        do {
            random.nextBytes(bytes);
        } while (allZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    String operationId() {
        return UUID.randomUUID().toString();
    }

    private static boolean allZero(byte[] value) {
        for (byte item : value) {
            if (item != 0) return false;
        }
        return true;
    }
}
