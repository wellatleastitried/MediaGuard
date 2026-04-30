package wellatleastitried.mediaguard.api;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguard.MediaGuardProperties;
import wellatleastitried.mediaguard.dto.BackupDto;
import wellatleastitried.mediaguard.dto.BackupRunDto;
import wellatleastitried.mediaguard.dto.ScheduleUpdateRequest;
import wellatleastitried.mediaguard.dto.ServerStatusDto;
import wellatleastitried.mediaguard.model.BackupArchive;
import wellatleastitried.mediaguard.model.BackupRunResult;
import wellatleastitried.mediaguard.service.BackupArchiveService;
import wellatleastitried.mediaguard.service.BackupOrchestratorService;

@RestController
@RequestMapping("/api/v1")
public class BackupController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupController.class);

    private final BackupArchiveService archiveService;
    private final BackupOrchestratorService orchestratorService;
    private final MediaGuardProperties properties;

    public BackupController(
        BackupArchiveService archiveService,
        BackupOrchestratorService orchestratorService,
        MediaGuardProperties properties
    ) {
        this.archiveService = archiveService;
        this.orchestratorService = orchestratorService;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ServerStatusDto health() {
        LOGGER.info("GET /api/v1/health — running={}, interval={}", orchestratorService.isRunning(), orchestratorService.getInterval());
        return new ServerStatusDto(
            true,
            orchestratorService.isRunning(),
            properties.getRetentionCount(),
            orchestratorService.getInterval()
        );
    }

    @PutMapping("/schedule")
    public ServerStatusDto updateSchedule(@RequestBody ScheduleUpdateRequest request) {
        LOGGER.info("PUT /api/v1/schedule — requestedInterval={}", request.backupInterval());
        Duration updated = orchestratorService.updateInterval(request.backupInterval());
        LOGGER.info("Schedule updated — newInterval={}", updated);
        return new ServerStatusDto(
            true,
            orchestratorService.isRunning(),
            properties.getRetentionCount(),
            updated
        );
    }

    @GetMapping("/backups")
    public List<BackupDto> listBackups() {
        LOGGER.info("GET /api/v1/backups");
        List<BackupDto> backups = archiveService.listArchives().stream().map(this::toDto).toList();
        LOGGER.info("Returning {} backup(s)", backups.size());
        return backups;
    }

    @GetMapping("/backups/latest")
    public BackupDto latestBackup() {
        LOGGER.info("GET /api/v1/backups/latest");
        return archiveService.latest()
            .map(a -> { LOGGER.info("Latest backup: {}", a.fileName()); return toDto(a); })
            .orElseThrow(() -> { LOGGER.warn("No backup archives found"); return new ResponseStatusException(HttpStatus.NOT_FOUND, "No backup archives found"); });
    }

    @GetMapping("/backups/{id}")
    public BackupDto backupById(@PathVariable String id) {
        LOGGER.info("GET /api/v1/backups/{}", id);
        return archiveService.byId(id)
            .map(this::toDto)
            .orElseThrow(() -> { LOGGER.warn("Backup not found: {}", id); return new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup not found"); });
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<Resource> downloadById(@PathVariable String id) {
        LOGGER.info("GET /api/v1/backups/{}/download", id);
        BackupArchive archive = archiveService.byId(id)
            .orElseThrow(() -> { LOGGER.warn("Download requested for missing backup: {}", id); return new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup not found"); });
        return downloadResponse(archive);
    }

    @GetMapping("/backups/latest/download")
    public ResponseEntity<Resource> downloadLatest() {
        LOGGER.info("GET /api/v1/backups/latest/download");
        BackupArchive archive = archiveService.latest()
            .orElseThrow(() -> { LOGGER.warn("Download requested but no archives exist"); return new ResponseStatusException(HttpStatus.NOT_FOUND, "No backup archives found"); });
        return downloadResponse(archive);
    }

    @DeleteMapping("/backups/{id}")
    public ResponseEntity<Void> deleteBackup(@PathVariable String id) {
        LOGGER.info("DELETE /api/v1/backups/{}", id);
        boolean deleted = archiveService.deleteById(id);
        if (!deleted) {
            LOGGER.warn("Delete failed — backup not found: {}", id);
            return ResponseEntity.notFound().build();
        }
        LOGGER.info("Backup deleted: {}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/backups/run")
    public BackupRunDto runBackup() {
        LOGGER.info("POST /api/v1/backups/run — triggering manual backup");
        BackupRunResult result = orchestratorService.runBackup();
        LOGGER.info("Manual backup complete — runId={}, services={}, archive={}, duration={}ms",
            result.runId(), result.servicesRan(), result.archive().fileName(),
            result.finishedAt().toEpochMilli() - result.startedAt().toEpochMilli());
        return new BackupRunDto(
            result.runId(),
            result.startedAt(),
            result.finishedAt(),
            result.servicesRan(),
            toDto(result.archive())
        );
    }

    private ResponseEntity<Resource> downloadResponse(BackupArchive archive) {
        Resource file = new FileSystemResource(archive.path());
        if (!file.exists()) {
            LOGGER.error("Archive file missing on disk: {}", archive.path());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup file missing on disk");
        }

        try {
            LOGGER.info("Serving download: {} ({} bytes)", archive.fileName(), archive.sizeBytes());
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archive.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.contentLength())
                .body(file);
        } catch (IOException e) {
            LOGGER.error("Failed to read backup file: {}", archive.path(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read backup file");
        }
    }

    private BackupDto toDto(BackupArchive archive) {
        return new BackupDto(archive.id(), archive.fileName(), archive.sizeBytes(), archive.createdAt());
    }
}
