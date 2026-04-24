package wellatleastitried.mediaguard.service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import wellatleastitried.mediaguard.MediaGuardProperties;
import wellatleastitried.mediaguard.model.BackupArchive;
import wellatleastitried.mediaguard.model.BackupRunResult;
import wellatleastitried.mediaguard.services.runner.Runner;

@Service
public class BackupOrchestratorService {

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

    @Scheduled(fixedDelay = 60000)
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

        try {
            for (Runner runner : runners) {
                runner.run(runDirectory);
            }

            BackupArchive archive = archiveService.createArchive(runDirectory);
            return new BackupRunResult(
                archive.id(),
                startedAt,
                Instant.now(),
                runners.stream().map(Runner::getServiceName).toList(),
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
