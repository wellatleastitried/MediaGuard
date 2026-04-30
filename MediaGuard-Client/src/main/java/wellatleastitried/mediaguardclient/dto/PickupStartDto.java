package wellatleastitried.mediaguardclient.dto;

public record PickupStartDto(
    String taskId,
    String backupId,
    String fileName,
    long totalBytes
) {
}
