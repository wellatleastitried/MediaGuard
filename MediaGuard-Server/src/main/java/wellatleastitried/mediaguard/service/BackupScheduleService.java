package wellatleastitried.mediaguard.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import wellatleastitried.mediaguard.MediaGuardProperties;

@Service
public class BackupScheduleService {

    private final AtomicReference<Duration> currentInterval;
    private final AtomicReference<Instant> nextRun;

    public BackupScheduleService(MediaGuardProperties properties) {
        Duration interval = safe(properties.getBackupInterval());
        this.currentInterval = new AtomicReference<>(interval);
        this.nextRun = new AtomicReference<>(Instant.now().plus(interval));
    }

    public Duration getInterval() {
        return currentInterval.get();
    }

    public Duration updateInterval(Duration interval) {
        Duration safe = safe(interval);
        currentInterval.set(safe);
        nextRun.set(Instant.now().plus(safe));
        return safe;
    }

    public boolean dueNow() {
        Instant now = Instant.now();
        Instant current = nextRun.get();
        return !now.isBefore(current);
    }

    public void markRunStarted() {
        nextRun.set(Instant.now().plus(currentInterval.get()));
    }

    private Duration safe(Duration interval) {
        if (interval == null || interval.isNegative() || interval.isZero()) {
            return Duration.ofHours(12);
        }
        return interval;
    }
}
