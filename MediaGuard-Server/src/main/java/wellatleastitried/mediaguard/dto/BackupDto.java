package wellatleastitried.mediaguard.dto;

import java.time.Instant;

public record BackupDto(
    String id,
    String fileName,
    long sizeBytes,
    Instant createdAt
) {
}
