package wellatleastitried.mediaguard.api;

import java.util.List;

import org.springframework.stereotype.Service;

import wellatleastitried.mediaguard.model.BackupArchive;
import wellatleastitried.mediaguard.service.BackupArchiveService;

@Service
public class MediaGuardService {

    private final BackupArchiveService archiveService;

    public MediaGuardService(BackupArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    public List<BackupArchive> listBackups() {
        return archiveService.listArchives();
    }
}
