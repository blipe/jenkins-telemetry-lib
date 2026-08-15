package io.jenkins.telemetry.jenkins;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import io.jenkins.telemetry.api.TelemetryStatus;
import io.jenkins.telemetry.core.TelemetryConfiguration;
import io.jenkins.telemetry.core.TelemetryJournalDrainer;
import jenkins.model.Jenkins;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Extension
public final class TelemetrySpoolDrainer extends AsyncPeriodicWork {
    private static final AtomicBoolean REQUESTED=new AtomicBoolean(true);
    public TelemetrySpoolDrainer(){super("Jenkins delivery telemetry spool drainer");}
    static void requestSoon(){REQUESTED.set(true);} @Override public long getInitialDelay(){return 15000L;} @Override public long getRecurrencePeriod(){return REQUESTED.get()?15000L:60000L;}
    @Override protected void execute(TaskListener listener)throws IOException,InterruptedException{REQUESTED.set(false);Path home=Jenkins.get().getRootDir().toPath();TelemetryConfiguration config=JenkinsTelemetryRuntime.loadConfiguration();int max=Integer.getInteger("jenkins.telemetry.maxJournalsPerSweep",100);PendingJournalRegistry.discoverOnce(home,Integer.getInteger("jenkins.telemetry.discoveryLimit",10000));List<Path> journals=PendingJournalRegistry.oldest(max);int scanned=0,pending=0;for(Path journal:journals){if(Thread.currentThread().isInterrupted())throw new InterruptedException();scanned++;if(!Files.isDirectory(journal)){PendingJournalRegistry.remove(journal);continue;}try{TelemetryStatus status=TelemetryJournalDrainer.drain(journal,config,JenkinsTelemetryRuntime.collectorFactories());long lag=status.collectorLag().values().stream().mapToLong(Long::longValue).sum();if(lag>0)pending++;else PendingJournalRegistry.remove(journal);}catch(Exception failure){pending++;listener.getLogger().println("[jenkins-telemetry] Journal replay failed for "+journal+": "+failure);}}if(scanned>0)listener.getLogger().println("[jenkins-telemetry] Journal sweep scanned="+scanned+" pending="+pending);if(pending>0)REQUESTED.set(true);}
}
