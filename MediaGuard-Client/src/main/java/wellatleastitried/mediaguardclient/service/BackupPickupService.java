package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguardclient.Configuration;
import wellatleastitried.mediaguardclient.dto.BackupDto;
import wellatleastitried.mediaguardclient.dto.PickupProgressDto;
import wellatleastitried.mediaguardclient.dto.PickupStartDto;

@Service
public class BackupPickupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupPickupService.class);
    private static final String AUTO_DOWNLOAD_SUFFIX = "-AD";

    private final Configuration configuration;
    private final ClientStateService clientStateService;
    private final ServerApiService serverApiService;
    private final ExecutorService pickupExecutor = Executors.newCachedThreadPool();
    private final Map<String, PickupTaskState> pickupTasks = new ConcurrentHashMap<>();
    private Instant nextPickup;

    private enum PickupStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private static final class PickupTaskState {
        private final String taskId;
        private final String backupId;
        private final String fileName;
        private volatile PickupStatus status;
        private volatile long bytesWritten;
        private volatile long totalBytes;
        private volatile Path savedPath;
        private volatile String error;

        private PickupTaskState(String taskId, String backupId, String fileName, long totalBytes) {
            this.taskId = taskId;
            this.backupId = backupId;
            this.fileName = fileName;
            this.totalBytes = totalBytes;
            this.status = PickupStatus.QUEUED;
            this.bytesWritten = 0L;
        }
    }

    public BackupPickupService(
        Configuration configuration,
        ClientStateService clientStateService,
        ServerApiService serverApiService
    ) {
        this.configuration = configuration;
        this.clientStateService = clientStateService;
        this.serverApiService = serverApiService;
        this.nextPickup = Instant.now().plus(clientStateService.getPickupInterval());
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduledPickup() {
        Instant now = Instant.now();
        if (now.isBefore(nextPickup)) {
            return;
        }
        LOGGER.info("Scheduled pickup triggered, next scheduled check was {}", nextPickup);
        try {
            Path result = pickupLatestAuto();
            LOGGER.info("Scheduled pickup complete: {}", result);
        } catch (RuntimeException ex) {
            LOGGER.warn("Scheduled pickup failed", ex);
        }
        nextPickup = now.plus(clientStateService.getPickupInterval());
        LOGGER.info("Next pickup scheduled at: {}", nextPickup);
    }

    public Path pickupLatest() {
        String activeServer = requireActiveServer();
        List<BackupDto> backups = serverApiService.listBackups(activeServer);
        BackupDto latest = latestRequired(backups);

        return pickupManual(activeServer, latest);
    }

    public PickupStartDto startPickupLatestTask() {
        String activeServer = requireActiveServer();
        List<BackupDto> backups = serverApiService.listBackups(activeServer);
        BackupDto latest = latestRequired(backups);
        return startPickupTask(activeServer, latest);
    }

    public PickupStartDto startPickupByIdTask(String backupId) {
        String activeServer = requireActiveServer();
        return startPickupTask(activeServer, requireBackup(activeServer, backupId));
    }

    public PickupProgressDto pickupProgress(String taskId) {
        PickupTaskState state = pickupTasks.get(taskId);
        if (state == null) {
            LOGGER.warn("Pickup task not found: {}", taskId);
            throw new IllegalStateException("Pickup task not found: " + taskId);
        }

        LOGGER.debug("Pickup task status requested: taskId={}, status={}, bytesWritten={}, totalBytes={}",
            taskId, state.status, state.bytesWritten, state.totalBytes);

        return new PickupProgressDto(
            state.taskId,
            state.status.name(),
            state.backupId,
            state.fileName,
            state.bytesWritten,
            state.totalBytes,
            state.savedPath == null ? null : state.savedPath.toString(),
            state.error
        );
    }

    public Path pickupById(String backupId) {
        String activeServer = requireActiveServer();
        return pickupManual(activeServer, requireBackup(activeServer, backupId));
    }

    private Path pickupLatestAuto() {
        String activeServer = requireActiveServer();
        List<BackupDto> backups = serverApiService.listBackups(activeServer);
        BackupDto latest = latestRequired(backups);

        Path downloadsDir = downloadsDirectory();
        Path existingPath = findExistingCopy(downloadsDir, latest);
        if (existingPath != null) {
            LOGGER.info("Auto pickup skipped for {} because local copy already exists at {}", latest.id(), existingPath);
            return existingPath;
        }

        Path latestAutoPath = downloadsDir.resolve(withAutoSuffix(latest.fileName()));
        LOGGER.info("Auto pickup downloading {} to {}", latest.fileName(), latestAutoPath);
        Path downloaded = serverApiService.downloadById(activeServer, latest.id(), latestAutoPath);
        deletePlainDuplicate(downloadsDir, latest);

        if (backups.size() > 1) {
            BackupDto secondMostRecent = backups.get(1);
            Path secondAutoPath = downloadsDir.resolve(withAutoSuffix(secondMostRecent.fileName()));
            if (Files.exists(secondAutoPath)) {
                try {
                    Files.deleteIfExists(secondAutoPath);
                    LOGGER.info("Auto pickup removed second-most-recent auto-downloaded backup {}", secondAutoPath.getFileName());
                } catch (IOException e) {
                    LOGGER.warn("Auto pickup failed to remove second-most-recent auto-downloaded backup {}", secondAutoPath, e);
                }
            }
        }

        return downloaded;
    }

    private Path findExistingCopy(Path downloadsDir, BackupDto backup) {
        Path autoPath = downloadsDir.resolve(withAutoSuffix(backup.fileName()));
        if (matchesBackup(autoPath, backup)) {
            return autoPath;
        }

        Path plainPath = downloadsDir.resolve(backup.fileName());
        if (matchesBackup(plainPath, backup)) {
            return plainPath;
        }

        return null;
    }

    private boolean matchesBackup(Path candidate, BackupDto backup) {
        if (!Files.exists(candidate)) {
            return false;
        }

        try {
            return Files.size(candidate) == backup.sizeBytes();
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect local file {} for backup {}", candidate, backup.id(), e);
            return false;
        }
    }

    private void deletePlainDuplicate(Path downloadsDir, BackupDto backup) {
        Path plainPath = downloadsDir.resolve(backup.fileName());
        Path autoPath = downloadsDir.resolve(withAutoSuffix(backup.fileName()));

        if (!Files.exists(autoPath) || !Files.exists(plainPath)) {
            return;
        }

        try {
            long autoSize = Files.size(autoPath);
            long plainSize = Files.size(plainPath);
            if (autoSize == backup.sizeBytes() && plainSize == backup.sizeBytes()) {
                Files.deleteIfExists(plainPath);
                LOGGER.info("Auto pickup removed plain duplicate for backup {} and kept {}", backup.id(), autoPath.getFileName());
            }
        } catch (IOException e) {
            LOGGER.warn("Auto pickup failed to reconcile plain duplicate for backup {}", backup.id(), e);
        }
    }

    private PickupStartDto startPickupTask(String activeServer, BackupDto backup) {
        Path downloadsDir = downloadsDirectory();
        Path existingPath = findExistingCopy(downloadsDir, backup);
        if (existingPath != null) {
            String taskId = UUID.randomUUID().toString();
            PickupTaskState existingState = new PickupTaskState(taskId, backup.id(), backup.fileName(), backup.sizeBytes());
            existingState.status = PickupStatus.COMPLETED;
            existingState.bytesWritten = backup.sizeBytes();
            existingState.savedPath = existingPath;
            pickupTasks.put(taskId, existingState);
            LOGGER.info("Pickup task {} reused existing local copy for backup {} at {}", taskId, backup.id(), existingPath);
            return new PickupStartDto(taskId, backup.id(), backup.fileName(), backup.sizeBytes());
        }

        Path destination = downloadsDir.resolve(backup.fileName());
        String taskId = UUID.randomUUID().toString();
        PickupTaskState state = new PickupTaskState(taskId, backup.id(), backup.fileName(), backup.sizeBytes());
        pickupTasks.put(taskId, state);

        LOGGER.info("Starting pickup task {} for backup {} to {}", taskId, backup.id(), destination);
        pickupExecutor.submit(() -> runPickupTask(activeServer, backup, destination, state));

        return new PickupStartDto(taskId, backup.id(), backup.fileName(), backup.sizeBytes());
    }

    private Path pickupManual(String activeServer, BackupDto backup) {
        Path downloadsDir = downloadsDirectory();
        Path existingPath = findExistingCopy(downloadsDir, backup);
        if (existingPath != null) {
            LOGGER.info("Manual pickup: skipping {} because client already has a local copy at {}", backup.id(), existingPath);
            return existingPath;
        }

        Path destination = downloadsDir.resolve(backup.fileName());
        LOGGER.info("Manual pickup: downloading {} to {}", backup.fileName(), destination);
        return serverApiService.downloadById(activeServer, backup.id(), destination);
    }

    private void runPickupTask(String activeServer, BackupDto backup, Path destination, PickupTaskState state) {
        state.status = PickupStatus.RUNNING;
        try {
            Path downloaded = serverApiService.downloadById(
                activeServer,
                backup.id(),
                destination,
                (bytesWritten, totalBytes) -> {
                    state.bytesWritten = bytesWritten;
                    if (totalBytes > 0) {
                        state.totalBytes = totalBytes;
                    }
                }
            );
            state.savedPath = downloaded;
            if (state.totalBytes <= 0) {
                state.totalBytes = state.bytesWritten;
            }
            state.status = PickupStatus.COMPLETED;
            LOGGER.info("Pickup task {} completed: {}", state.taskId, downloaded);
        } catch (RuntimeException ex) {
            state.status = PickupStatus.FAILED;
            state.error = ex.getMessage();
            LOGGER.warn("Pickup task {} failed for backup {}", state.taskId, backup.id(), ex);
        }
    }

    private BackupDto requireBackup(String activeServer, String backupId) {
        return serverApiService.listBackups(activeServer).stream()
            .filter(backup -> backup.id().equals(backupId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Backup not found: " + backupId));
    }

    private String requireActiveServer() {
        String activeServer = clientStateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            LOGGER.error("Pickup requested but no active server is configured");
            throw new IllegalStateException("No active server selected");
        }
        return activeServer;
    }

    private BackupDto latestRequired(List<BackupDto> backups) {
        if (backups == null || backups.isEmpty()) {
            throw new IllegalStateException("No backups found on server");
        }
        return backups.get(0);
    }

    private Path downloadsDirectory() {
        return Path.of(configuration.getDownloadDirectory()).toAbsolutePath().normalize();
    }

    private String withAutoSuffix(String fileName) {
        if (fileName.endsWith(".zip")) {
            return fileName.substring(0, fileName.length() - 4) + AUTO_DOWNLOAD_SUFFIX + ".zip";
        }
        return fileName + AUTO_DOWNLOAD_SUFFIX;
    }

    @PreDestroy
    public void shutdownPickupExecutor() {
        pickupExecutor.shutdownNow();
    }

    public Duration updatePickupInterval(Duration interval) {
        Duration updated = clientStateService.setPickupInterval(interval);
        nextPickup = Instant.now().plus(updated);
        return updated;
    }
}
