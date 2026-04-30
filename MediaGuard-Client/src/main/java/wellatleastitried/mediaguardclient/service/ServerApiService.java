package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import wellatleastitried.mediaguardclient.api.Endpoints;
import wellatleastitried.mediaguardclient.dto.BackupDto;
import wellatleastitried.mediaguardclient.dto.ServerStatusDto;

@Service
public class ServerApiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApiService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();

    // Single shared RestClient — follows redirects and uses absolute URIs per request.
    private final RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(httpClient))
        .build();

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

        URI uri = URI.create(serverUrl + Endpoints.SERVER_BACKUP_LATEST_DOWNLOAD.getPath());
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String snippet;
                try (InputStream errorBody = response.body()) {
                    snippet = new String(errorBody.readNBytes(256));
                }
                LOGGER.error("Latest backup download failed from {} with HTTP {}: {}", serverUrl, status, snippet);
                throw new IllegalStateException("Latest backup download failed with HTTP " + status);
            }

            Files.createDirectories(destinationPath.getParent());
            Path tempPath = destinationPath.resolveSibling(destinationPath.getFileName().toString() + ".tmp");

            try (InputStream inputStream = response.body()) {
                Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!looksLikeZip(tempPath)) {
                long size = Files.size(tempPath);
                Files.deleteIfExists(tempPath);
                LOGGER.error("Latest backup download was not a zip payload from {} ({} bytes)", serverUrl, size);
                throw new IllegalStateException("Server returned non-zip content for latest backup download");
            }

            Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Download complete: {} ({} bytes)", destinationPath.getFileName(), Files.size(destinationPath));
            return destinationPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Latest backup download interrupted for {}", serverUrl, e);
            throw new IllegalStateException("Latest backup download interrupted", e);
        } catch (IOException e) {
            LOGGER.error("Failed to persist downloaded backup to {}", destinationPath, e);
            throw new IllegalStateException("Failed to persist downloaded backup", e);
        }
    }

    private boolean looksLikeZip(Path path) throws IOException {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            int read = in.read(header);
            if (read < 4) {
                return false;
            }
        }

        return header[0] == 'P'
            && header[1] == 'K'
            && ((header[2] == 3 && header[3] == 4)
                || (header[2] == 5 && header[3] == 6)
                || (header[2] == 7 && header[3] == 8));
    }
}
