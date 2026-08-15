package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.ExportBatch;
import io.jenkins.telemetry.api.ExportResult;
import io.jenkins.telemetry.api.ExportStatus;
import io.jenkins.telemetry.api.Signal;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class TelemetryExporter implements AutoCloseable {
    private static final ScheduledExecutorService WORKERS = Executors.newScheduledThreadPool(
        Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
        new DaemonThreadFactory());

    private final TelemetryJournal journal;
    private final List<CollectorBinding> collectors;
    private final int batchRecords;
    private final int batchBytes;
    private final Duration timeout;
    private final Duration initialDelay;
    private final Duration maximumDelay;
    private final boolean automatic;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile boolean closed;
    private volatile long currentDelayMillis;
    private volatile long requestedRetryAfterMillis;

    TelemetryExporter(TelemetryJournal journal, List<CollectorBinding> collectors,
                      int batchRecords, int batchBytes, Duration timeout,
                      Duration initialDelay, Duration maximumDelay, boolean automatic)
        throws IOException {
        this.journal = journal;
        this.collectors = List.copyOf(collectors);
        this.batchRecords = batchRecords;
        this.batchBytes = batchBytes;
        this.timeout = timeout;
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
        this.automatic = automatic;
        this.currentDelayMillis = Math.max(1, initialDelay.toMillis());
        journal.configureCollectors(collectorMap(collectors));
    }

    void requestExport() {
        if (!automatic || collectors.isEmpty() || closed) return;
        schedule(0);
    }

    synchronized void flush() {
        if (closed) return;
        drainAll();
    }

    Map<String, Long> collectorLag() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (CollectorBinding binding : collectors) {
            long total = 0;
            for (Signal signal : binding.signals()) {
                total += Math.max(0, journal.latestSequence(signal)
                    - journal.acknowledgedSequence(binding.definition().id(), signal));
            }
            result.put(binding.definition().id(), total);
        }
        return result;
    }

    private void schedule(long delayMillis) {
        if (!scheduled.compareAndSet(false, true)) return;
        WORKERS.schedule(this::runScheduled, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
    }

    private void runScheduled() {
        try {
            if (closed) return;
            boolean fullyDrained;
            requestedRetryAfterMillis = 0;
            synchronized (this) {
                fullyDrained = drainAll();
            }
            if (fullyDrained) {
                currentDelayMillis = Math.max(1, initialDelay.toMillis());
            } else {
                long retry = Math.max(currentDelayMillis, requestedRetryAfterMillis);
                currentDelayMillis = Math.min(maximumDelay.toMillis(),
                    Math.max(currentDelayMillis + 1, currentDelayMillis * 2));
                scheduled.set(false);
                schedule(withJitter(Math.min(maximumDelay.toMillis(), retry)));
                return;
            }
        } finally {
            scheduled.set(false);
            if (!closed && automatic && totalLag() > 0) {
                schedule(withJitter(currentDelayMillis));
            }
        }
    }

    private boolean drainAll() {
        boolean blocked = false;
        for (CollectorBinding binding : collectors) {
            for (Signal signal : binding.signals()) {
                if (!binding.collector().capabilities().supports(signal)) continue;
                if (!exportSignal(binding, signal)) blocked = true;
            }
            try {
                binding.collector().flush().toCompletableFuture()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                blocked = true;
            }
        }
        return !blocked && totalLag() == 0;
    }

    private long totalLag() {
        long total = 0;
        for (long lag : collectorLag().values()) total += lag;
        return total;
    }

    private boolean exportSignal(CollectorBinding binding, Signal signal) {
        while (!closed) {
            JournalRead read;
            try {
                int preferredRecords = Math.min(batchRecords,
                    binding.collector().capabilities().preferredBatchRecords());
                int preferredBytes = Math.min(batchBytes,
                    binding.collector().capabilities().preferredBatchBytes());
                read = journal.read(binding.definition().id(), signal, preferredRecords, preferredBytes);
            } catch (IOException e) {
                return false;
            }
            if (read.isEmpty()) return true;

            ExportResult result;
            try {
                result = binding.collector()
                    .export(new ExportBatch(binding.definition().id(), signal, read.entries()))
                    .toCompletableFuture()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return false;
            }

            if (result.retryAfterMillis() > 0) {
                requestedRetryAfterMillis = Math.max(requestedRetryAfterMillis,
                    result.retryAfterMillis());
            }

            boolean acknowledge = result.acknowledged()
                || (result.status() == ExportStatus.PERMANENT_FAILURE
                    && binding.definition().acknowledgePermanentFailures());
            if (!acknowledge) return false;

            try {
                journal.acknowledge(binding.definition().id(), signal,
                    read.nextSequence(), read.nextOffset());
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void close() {
        closed = true;
        List<Exception> failures = new ArrayList<>();
        for (CollectorBinding binding : collectors) {
            try {
                binding.collector().close();
            } catch (Exception e) {
                failures.add(e);
            }
        }
    }

    private static Map<Signal, Set<String>> collectorMap(List<CollectorBinding> bindings) {
        Map<Signal, Set<String>> result = new EnumMap<>(Signal.class);
        for (Signal signal : Signal.values()) result.put(signal, new LinkedHashSet<>());
        for (CollectorBinding binding : bindings) {
            for (Signal signal : binding.signals()) {
                if (binding.collector().capabilities().supports(signal)) {
                    result.get(signal).add(binding.definition().id());
                }
            }
        }
        return result;
    }

    private static long withJitter(long delay) {
        if (delay <= 1) return delay;
        long spread = Math.max(1, delay / 5);
        long random = java.util.concurrent.ThreadLocalRandom.current().nextLong(-spread, spread + 1);
        return Math.max(1, delay + random);
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final java.util.concurrent.atomic.AtomicInteger sequence =
            new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task,
                "jenkins-telemetry-export-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, failure) -> { });
            return thread;
        }
    }
}
