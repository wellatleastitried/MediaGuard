package wellatleastitried.mediaguard.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService runnerExecutor = Executors.newCachedThreadPool();

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
            return;
        }
        if (!scheduleService.dueNow()) {
            return;
        }
        try {
            runBackup();
        } catch (RuntimeException ex) {
            LOGGER.error("Scheduled backup run failed", ex);
        }
    }

    public BackupRunResult runBackup() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A backup run is already in progress");
        }

        scheduleService.markRunStarted();

        Path runDirectory = archiveService.createRunDirectory();
        Instant startedAt = Instant.now();
        List<Runner> runners = runnerFactory.build(properties.getServices());
        List<String> servicesCompleted = new ArrayList<>();

        try {
            List<Future<RunnerOutcome>> futures = new ArrayList<>();
            for (Runner runner : runners) {
                futures.add(runnerExecutor.submit(() -> runSingleRunner(runner, runDirectory)));
            }

            for (Future<RunnerOutcome> future : futures) {
                try {
                    RunnerOutcome outcome = future.get();
                    if (outcome.success()) {
                        servicesCompleted.add(outcome.serviceName());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Backup run interrupted", ex);
                } catch (ExecutionException ex) {
                    LOGGER.warn("Unexpected failure while waiting for runner task", ex.getCause());
                }
            }

            BackupArchive archive = archiveService.createArchive(runDirectory);
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
        try {
            runner.run(runDirectory);
            return new RunnerOutcome(runner.getServiceName(), true);
        } catch (RuntimeException ex) {
            LOGGER.warn("Backup runner failed for service {}. Continuing with remaining services.", runner.getServiceName(), ex);
            return new RunnerOutcome(runner.getServiceName(), false);
        }
    }

    private record RunnerOutcome(String serviceName, boolean success) {
    }
}
