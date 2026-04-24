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

    private final MediaGuardService mediaGuardService;
    private final BackupArchiveService archiveService;
    private final BackupOrchestratorService orchestratorService;
    private final MediaGuardProperties properties;

    public BackupController(
        MediaGuardService mediaGuardService,
        BackupArchiveService archiveService,
        BackupOrchestratorService orchestratorService,
        MediaGuardProperties properties
    ) {
        this.mediaGuardService = mediaGuardService;
        this.archiveService = archiveService;
        this.orchestratorService = orchestratorService;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ServerStatusDto health() {
        return new ServerStatusDto(
            true,
            orchestratorService.isRunning(),
            properties.getRetentionCount(),
            orchestratorService.getInterval()
        );
    }

    @PutMapping("/schedule")
    public ServerStatusDto updateSchedule(@RequestBody ScheduleUpdateRequest request) {
        Duration updated = orchestratorService.updateInterval(request.backupInterval());
        return new ServerStatusDto(
            true,
            orchestratorService.isRunning(),
            properties.getRetentionCount(),
            updated
        );
    }

    @GetMapping("/backups")
    public List<BackupDto> listBackups() {
        return mediaGuardService.listBackups().stream().map(this::toDto).toList();
    }

    @GetMapping("/backups/latest")
    public BackupDto latestBackup() {
        return archiveService.latest()
            .map(this::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No backup archives found"));
    }

    @GetMapping("/backups/{id}")
    public BackupDto backupById(@PathVariable String id) {
        return archiveService.byId(id)
            .map(this::toDto)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup not found"));
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<Resource> downloadById(@PathVariable String id) {
        BackupArchive archive = archiveService.byId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup not found"));
        return downloadResponse(archive);
    }

    @GetMapping("/backups/latest/download")
    public ResponseEntity<Resource> downloadLatest() {
        BackupArchive archive = archiveService.latest()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No backup archives found"));
        return downloadResponse(archive);
    }

    @DeleteMapping("/backups/{id}")
    public ResponseEntity<Void> deleteBackup(@PathVariable String id) {
        boolean deleted = archiveService.deleteById(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/backups/run")
    public BackupRunDto runBackup() {
        BackupRunResult result = orchestratorService.runBackup();
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup file missing on disk");
        }

        try {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archive.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.contentLength())
                .body(file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read backup file");
        }
    }

    private BackupDto toDto(BackupArchive archive) {
        return new BackupDto(archive.id(), archive.fileName(), archive.sizeBytes(), archive.createdAt());
    }
}
