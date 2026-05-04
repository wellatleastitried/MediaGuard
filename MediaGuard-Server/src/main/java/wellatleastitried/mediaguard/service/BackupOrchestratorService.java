package wellatleastitried.mediaguard.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguard.MediaGuardProperties;
import wellatleastitried.mediaguard.model.BackupArchive;
import wellatleastitried.mediaguard.model.BackupRunResult;
import wellatleastitried.mediaguard.services.runner.Runner;

@Service
public class BackupOrchestratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupOrchestratorService.class);

    private final MediaGuardProperties properties;
    private final RunnerFactory runnerFactory;
    private final BackupArchiveService archiveService;
    private final BackupScheduleService scheduleService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService runnerExecutor = new ThreadPoolExecutor(
        1, // core threads
        6, // max threads
        30, // keep-alive time
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>()
    );

    public BackupOrchestratorService(
        MediaGuardProperties properties,
        RunnerFactory runnerFactory,
        BackupArchiveService archiveService,
        BackupScheduleService scheduleService
    ) {
        this.properties = properties;
        this.runnerFactory = runnerFactory;
        this.archiveService = archiveService;
        this.scheduleService = scheduleService;
    }

    @Scheduled(fixedDelay = 10000)
    public void scheduledRun() {
        if (running.get()) {
            LOGGER.debug("Scheduled check skipped, backup already in progress");
            return;
        }
        if (!scheduleService.dueNow()) {
            return;
        }
        LOGGER.info("Scheduled backup triggered, next run at {}", scheduleService.getNextRun());
        try {
            runBackup();
        } catch (RuntimeException ex) {
            LOGGER.error("Scheduled backup run failed", ex);
        }
    }

    public BackupRunResult runBackup() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("runBackup() called while backup already in progress, rejecting");
            throw new IllegalStateException("A backup run is already in progress");
        }

        scheduleService.markRunStarted();

        Path runDirectory = archiveService.createRunDirectory();
        Instant startedAt = Instant.now();
        List<Runner> runners = runnerFactory.build(properties.getServices());
        List<String> servicesCompleted = new ArrayList<>();

        LOGGER.info("Backup run started: runDir={}, services={}", runDirectory, runners.stream().map(Runner::getServiceName).toList());

        try {
            List<Future<RunnerOutcome>> futures = new ArrayList<>();
            for (Runner runner : runners) {
                LOGGER.info("Submitting runner for service: {}", runner.getServiceName());
                futures.add(runnerExecutor.submit(() -> runSingleRunner(runner, runDirectory)));
            }

            for (Future<RunnerOutcome> future : futures) {
                try {
                    RunnerOutcome outcome = future.get();
                    if (outcome.success()) {
                        servicesCompleted.add(outcome.serviceName());
                        LOGGER.info("Runner succeeded: {}", outcome.serviceName());
                    } else {
                        LOGGER.warn("Runner failed (non-fatal): {}", outcome.serviceName());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Backup run interrupted", ex);
                } catch (ExecutionException ex) {
                    LOGGER.warn("Unexpected failure while waiting for runner task", ex.getCause());
                }
            }

            LOGGER.info("All runners finished: succeeded={}/{}", servicesCompleted.size(), runners.size());
            BackupArchive archive = archiveService.createArchive(runDirectory);
            long durationMs = Instant.now().toEpochMilli() - startedAt.toEpochMilli();
            LOGGER.info("Backup run complete: archive={}, size={}B, duration={}ms, services={}",
                archive.fileName(), archive.sizeBytes(), durationMs, servicesCompleted);
            return new BackupRunResult(
                archive.id(),
                startedAt,
                Instant.now(),
                servicesCompleted,
                archive
            );
        } finally {
            archiveService.removeRunDirectory(runDirectory);
            running.set(false);
            LOGGER.debug("Run directory cleaned up: {}", runDirectory);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Duration getInterval() {
        return scheduleService.getInterval();
    }

    public Duration updateInterval(Duration duration) {
        return scheduleService.updateInterval(duration);
    }

    @PreDestroy
    public void shutdownRunnerExecutor() {
        runnerExecutor.shutdownNow();
    }

    private RunnerOutcome runSingleRunner(Runner runner, Path runDirectory) {
        LOGGER.info("[{}] Runner starting", runner.getServiceName());
        long start = System.currentTimeMillis();
        try {
            runner.run(runDirectory);
            LOGGER.info("[{}] Runner finished in {}ms", runner.getServiceName(), System.currentTimeMillis() - start);
            return new RunnerOutcome(runner.getServiceName(), true);
        } catch (RuntimeException ex) {
            LOGGER.error("[{}] Runner failed after {}ms, continuing with remaining services",
                runner.getServiceName(), System.currentTimeMillis() - start, ex);
            return new RunnerOutcome(runner.getServiceName(), false);
        }
    }

    private record RunnerOutcome(String serviceName, boolean success) {
    }
}
