package io.jenkins.telemetry.verification;

import org.junit.jupiter.api.Test;

final class CoreSelfTestTest {
    @Test void runsEndToEndVerification() throws Exception { CoreSelfTest.main(new String[0]); }
}
