package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    @FunctionalInterface
    public interface DownloadProgressListener {
        void onProgress(long bytesWritten, long totalBytes);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerApiService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build();

    private final RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(httpClient))
        .build();

    public ServerStatusDto health(String serverUrl) {
        LOGGER.info("Probing server health: {}", serverUrl);
        ServerStatusDto dto = restClient.get()
            .uri(serverUrl + Endpoints.SERVER_HEALTH.getPath())
            .retrieve()
            .body(ServerStatusDto.class);
        LOGGER.info("Server health OK: {}, running={}, interval={}", serverUrl,
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
        return downloadLatest(serverUrl, destinationPath, null);
    }

    public Path downloadLatest(String serverUrl, Path destinationPath, DownloadProgressListener progressListener) {
        LOGGER.info("Downloading latest backup from {} to {}", serverUrl, destinationPath);

        return downloadFromUri(
            URI.create(serverUrl + Endpoints.SERVER_BACKUP_LATEST_DOWNLOAD.getPath()),
            destinationPath,
            "latest backup",
            progressListener
        );
    }

    public Path downloadById(String serverUrl, String backupId, Path destinationPath) {
        return downloadById(serverUrl, backupId, destinationPath, null);
    }

    public Path downloadById(String serverUrl, String backupId, Path destinationPath, DownloadProgressListener progressListener) {
        LOGGER.info("Downloading backup id={} from {} to {}", backupId, serverUrl, destinationPath);

        return downloadFromUri(
            URI.create(serverUrl + Endpoints.SERVER_BACKUPS.getPath() + "/" + backupId + "/download"),
            destinationPath,
            "backup " + backupId,
            progressListener
        );
    }

    private Path downloadFromUri(URI uri, Path destinationPath, String operationName, DownloadProgressListener progressListener) {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String snippet;
                try (InputStream errorBody = response.body()) {
                    snippet = new String(errorBody.readNBytes(256));
                }
                LOGGER.error("{} download failed from {} with HTTP {}: {}", operationName, uri, status, snippet);
                throw new IllegalStateException(operationName + " download failed with HTTP " + status);
            }

            Files.createDirectories(destinationPath.getParent());
            Path tempPath = destinationPath.resolveSibling(destinationPath.getFileName().toString() + ".tmp");
            long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);

            try (InputStream inputStream = response.body();
                 OutputStream outputStream = Files.newOutputStream(
                     tempPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE
                 )) {
                byte[] buffer = new byte[8192];
                long bytesWritten = 0L;
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    bytesWritten += read;
                    if (progressListener != null) {
                        progressListener.onProgress(bytesWritten, totalBytes);
                    }
                }
            }

            if (!looksLikeZip(tempPath)) {
                long size = Files.size(tempPath);
                Files.deleteIfExists(tempPath);
                LOGGER.error("{} download returned non-zip payload from {} ({} bytes)", operationName, uri, size);
                throw new IllegalStateException("Server returned non-zip content for " + operationName + " download");
            }

            Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Download complete: {} ({} bytes)", destinationPath.getFileName(), Files.size(destinationPath));
            return destinationPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("{} download interrupted for {}", operationName, uri, e);
            throw new IllegalStateException(operationName + " download interrupted", e);
        } catch (IOException e) {
            LOGGER.error("Failed to persist {} download to {}", operationName, destinationPath, e);
            throw new IllegalStateException("Failed to persist " + operationName + " download", e);
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
