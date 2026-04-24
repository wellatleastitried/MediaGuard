package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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

    public ServerStatusDto health(String serverUrl) {
        return RestClient.builder()
            .baseUrl(serverUrl)
            .build()
            .get()
            .uri(Endpoints.SERVER_HEALTH.getPath())
            .retrieve()
            .body(ServerStatusDto.class);
    }

    public List<BackupDto> listBackups(String serverUrl) {
        List<BackupDto> response = RestClient.builder()
            .baseUrl(serverUrl)
            .build()
            .get()
            .uri(Endpoints.SERVER_BACKUPS.getPath())
            .retrieve()
            .body(new ParameterizedTypeReference<List<BackupDto>>() {
            });
        return response == null ? List.of() : response;
    }

    public void triggerBackup(String serverUrl) {
        RestClient.builder()
            .baseUrl(serverUrl)
            .build()
            .post()
            .uri(Endpoints.SERVER_BACKUP_RUN.getPath())
            .retrieve()
            .toBodilessEntity();
    }

    public void updateServerSchedule(String serverUrl, Duration interval) {
        RestClient.builder()
            .baseUrl(serverUrl)
            .build()
            .put()
            .uri(Endpoints.SERVER_SCHEDULE.getPath())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"backupInterval\":\"" + interval + "\"}")
            .retrieve()
            .toBodilessEntity();
    }

    public Path downloadLatest(String serverUrl, Path destinationPath) {
        Resource resource = RestClient.builder()
            .baseUrl(serverUrl)
            .build()
            .get()
            .uri(Endpoints.SERVER_BACKUP_LATEST_DOWNLOAD.getPath())
            .retrieve()
            .body(Resource.class);

        if (resource == null) {
            throw new IllegalStateException("No file returned from server");
        }

        try {
            Files.createDirectories(destinationPath.getParent());
            Files.copy(resource.getInputStream(), destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return destinationPath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist downloaded backup", e);
        }
    }
}
