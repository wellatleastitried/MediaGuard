package wellatleastitried.mediaguard.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
        if (scheduleService.dueNow()) {
            runBackup();
        }
    }

    public BackupRunResult runBackup() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A backup run is already in progress");
        }

        Path runDirectory = archiveService.createRunDirectory();
        Instant startedAt = Instant.now();
        List<Runner> runners = runnerFactory.build(properties.getServices());
        List<String> servicesCompleted = new ArrayList<>();

        try {
            for (Runner runner : runners) {
                try {
                    runner.run(runDirectory);
                    servicesCompleted.add(runner.getServiceName());
                } catch (RuntimeException ex) {
                    LOGGER.warn("Backup runner failed for service {}. Continuing with remaining services.", runner.getServiceName(), ex);
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
}
