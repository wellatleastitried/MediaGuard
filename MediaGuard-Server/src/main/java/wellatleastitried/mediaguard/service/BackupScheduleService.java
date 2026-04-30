package wellatleastitried.mediaguard.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import wellatleastitried.mediaguard.MediaGuardProperties;

@Service
public class BackupScheduleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupScheduleService.class);

    private final AtomicReference<Duration> currentInterval;
    private final AtomicReference<Instant> nextRun;

    public BackupScheduleService(MediaGuardProperties properties) {
        Duration interval = safe(properties.getBackupInterval());
        this.currentInterval = new AtomicReference<>(interval);
        this.nextRun = new AtomicReference<>(Instant.now().plus(interval));
        LOGGER.info("Backup schedule initialized — interval={}, firstRunAt={}", interval, nextRun.get());
    }

    public Duration getInterval() {
        return currentInterval.get();
    }

    public Instant getNextRun() {
        return nextRun.get();
    }

    public Duration updateInterval(Duration interval) {
        Duration safe = safe(interval);
        currentInterval.set(safe);
        Instant next = Instant.now().plus(safe);
        nextRun.set(next);
        LOGGER.info("Backup interval updated — newInterval={}, nextRunAt={}", safe, next);
        return safe;
    }

    public boolean dueNow() {
        Instant now = Instant.now();
        Instant current = nextRun.get();
        return !now.isBefore(current);
    }

    public void markRunStarted() {
        Instant next = Instant.now().plus(currentInterval.get());
        nextRun.set(next);
        LOGGER.info("Backup run started — nextScheduledRun={}", next);
    }

    private Duration safe(Duration interval) {
        if (interval == null || interval.isNegative() || interval.isZero()) {
            return Duration.ofHours(12);
        }
        return interval;
    }
}
