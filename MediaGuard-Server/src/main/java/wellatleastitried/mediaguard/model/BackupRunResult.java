package wellatleastitried.mediaguard.model;

import java.time.Instant;
import java.util.List;

public record BackupRunResult(
    String runId,
    Instant startedAt,
    Instant finishedAt,
    List<String> servicesRan,
    BackupArchive archive
) {
}
