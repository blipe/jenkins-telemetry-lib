package io.jenkins.telemetry.verification;

import io.jenkins.telemetry.api.*;
import io.jenkins.telemetry.collector.file.FileTelemetryCollectorFactory;
import io.jenkins.telemetry.core.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class CoreSelfTest {
    private CoreSelfTest(){}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("jenkins-telemetry-core-");
        TelemetryConfiguration config=new TelemetryConfiguration(16*1024*1024,1024*1024,true,false,128,1024*1024,Duration.ofSeconds(5),Duration.ofMillis(10),Duration.ofSeconds(1),List.of(),"***",List.of(new CollectorDefinition("file","file",EnumSet.allOf(Signal.class),Map.of("directory",root.resolve("export").toString()),true)));
        try(TelemetryEngine telemetry=TelemetryEngine.builder(root.resolve("journal")).configuration(config).collectorFactory(new FileTelemetryCollectorFactory()).build()){
            telemetry.release(ReleaseIdentity.builder().serviceNamespace("verification").serviceName("orders").serviceVersion("1.0.0").sourceRevision("abcdef1234567890").build());
            telemetry.logs().info("build started",Attributes.of("password","must-redact"));telemetry.metrics().counter("tests.executed").add(12);
            OperationHandle operation=telemetry.beginOperation(OperationSpec.builder("verify",OperationKind.TEST).build());operation.event("tests.completed",Attributes.of("count",12));operation.succeed();
            ArtifactRef artifact=ArtifactRef.file("orders.jar","a".repeat(64),"file:///orders.jar");telemetry.artifact(artifact);DeploymentHandle deployment=telemetry.beginDeployment(DeploymentSpec.builder("production","orders").artifact(artifact).build());deployment.succeed();
            check(telemetry.status().logRecords()>0,"logs");check(telemetry.status().metricRecords()>0,"metrics");check(telemetry.status().traceRecords()>0,"traces");telemetry.flush();
        }
        check(Files.exists(root.resolve("export/logs.ndjson")),"logs exported");check(Files.exists(root.resolve("export/metrics.ndjson")),"metrics exported");check(Files.exists(root.resolve("export/traces.ndjson")),"traces exported");System.out.println("SELFTEST PASSED: "+root);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
