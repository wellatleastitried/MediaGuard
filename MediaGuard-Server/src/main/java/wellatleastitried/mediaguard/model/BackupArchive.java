package wellatleastitried.mediaguard.model;

import java.nio.file.Path;
import java.time.Instant;

public record BackupArchive(
    String id,
    String fileName,
    Path path,
    long sizeBytes,
    Instant createdAt
) {
}
