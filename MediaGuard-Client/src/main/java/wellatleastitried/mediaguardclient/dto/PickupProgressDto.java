package wellatleastitried.mediaguardclient.dto;

public record PickupProgressDto(
    String taskId,
    String status,
    String backupId,
    String fileName,
    long bytesWritten,
    long totalBytes,
    String savedPath,
    String error
) {
}
