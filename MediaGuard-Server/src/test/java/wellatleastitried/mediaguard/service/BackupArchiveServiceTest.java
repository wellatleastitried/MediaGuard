package wellatleastitried.mediaguard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import wellatleastitried.mediaguard.MediaGuardProperties;

class BackupArchiveServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsArchiveAndEnforcesRetention() throws Exception {
        MediaGuardProperties properties = new MediaGuardProperties();
        properties.setBackupRoot(tempDir.resolve("backups").toString());
        properties.setRetentionCount(2);

        BackupArchiveService service = new BackupArchiveService(properties);

        for (int i = 0; i < 3; i++) {
            Path run = service.createRunDirectory();
            Files.writeString(run.resolve("marker" + i + ".txt"), "ok");
            service.createArchive(run);
            service.removeRunDirectory(run);
            Thread.sleep(10);
        }

        assertEquals(2, service.listArchives().size());
        assertTrue(service.latest().isPresent());
    }
}
