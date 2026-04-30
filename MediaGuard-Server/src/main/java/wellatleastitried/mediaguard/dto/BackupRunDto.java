package wellatleastitried.mediaguard.dto;

import java.time.Instant;
import java.util.List;

public record BackupRunDto(
    String runId,
    Instant startedAt,
    Instant finishedAt,
    List<String> servicesRan,
    BackupDto archive
) {
}
