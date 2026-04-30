package wellatleastitried.mediaguard.dto;

import java.time.Duration;

public record ScheduleUpdateRequest(Duration backupInterval) {
}
