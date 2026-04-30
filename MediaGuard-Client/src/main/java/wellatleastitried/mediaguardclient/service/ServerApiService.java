package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import wellatleastitried.mediaguardclient.api.Endpoints;
import wellatleastitried.mediaguardclient.dto.BackupDto;
import wellatleastitried.mediaguardclient.dto.ServerStatusDto;

@Service
public class ServerApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApiService.class);

    // Single shared RestClient — thread-safe once built. Base URL is set per-request via absolute URIs.
    private final RestClient restClient = RestClient.create();

    public ServerStatusDto health(String serverUrl) {
        LOGGER.info("Probing server health: {}", serverUrl);
        ServerStatusDto dto = restClient.get()
            .uri(serverUrl + Endpoints.SERVER_HEALTH.getPath())
            .retrieve()
            .body(ServerStatusDto.class);
        LOGGER.info("Server health OK: {} — running={}, interval={}", serverUrl,
            dto != null ? dto.running() : "?", dto != null ? dto.backupInterval() : "?");
        return dto;
    }

    public List<BackupDto> listBackups(String serverUrl) {
        LOGGER.info("Fetching backup list from: {}", serverUrl);
        List<BackupDto> response = restClient.get()
            .uri(serverUrl + Endpoints.SERVER_BACKUPS.getPath())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        List<BackupDto> result = response == null ? List.of() : response;
        LOGGER.info("Received {} backup(s) from {}", result.size(), serverUrl);
        return result;
    }

    public void triggerBackup(String serverUrl) {
        LOGGER.info("Triggering backup on: {}", serverUrl);
        restClient.post()
            .uri(serverUrl + Endpoints.SERVER_BACKUP_RUN.getPath())
            .retrieve()
            .toBodilessEntity();
        LOGGER.info("Backup trigger acknowledged by: {}", serverUrl);
    }

    public void updateServerSchedule(String serverUrl, Duration interval) {
        LOGGER.info("Updating server schedule on {} to interval={}", serverUrl, interval);
        restClient.put()
            .uri(serverUrl + Endpoints.SERVER_SCHEDULE.getPath())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"backupInterval\":\"" + interval + "\"}")
            .retrieve()
            .toBodilessEntity();
        LOGGER.info("Server schedule update acknowledged by: {}", serverUrl);
    }

    public Path downloadLatest(String serverUrl, Path destinationPath) {
        LOGGER.info("Downloading latest backup from {} to {}", serverUrl, destinationPath);
        Resource resource = restClient.get()
            .uri(serverUrl + Endpoints.SERVER_BACKUP_LATEST_DOWNLOAD.getPath())
            .retrieve()
            .body(Resource.class);

        if (resource == null) {
            LOGGER.error("No file returned from server: {}", serverUrl);
            throw new IllegalStateException("No file returned from server");
        }

        try {
            Files.createDirectories(destinationPath.getParent());
            Files.copy(resource.getInputStream(), destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Download complete: {} ({} bytes)", destinationPath.getFileName(), Files.size(destinationPath));
            return destinationPath;
        } catch (IOException e) {
            LOGGER.error("Failed to persist downloaded backup to {}", destinationPath, e);
            throw new IllegalStateException("Failed to persist downloaded backup", e);
        }
    }
}
