package wellatleastitried.mediaguard.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

import wellatleastitried.mediaguard.MediaGuardProperties;
import wellatleastitried.mediaguard.model.BackupArchive;

@Service
public class BackupArchiveService {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);

    private final MediaGuardProperties properties;

    public BackupArchiveService(MediaGuardProperties properties) {
        this.properties = properties;
    }

    public Path rootDirectory() {
        return Path.of(properties.getBackupRoot()).toAbsolutePath().normalize();
    }

    public BackupArchive createArchive(Path runDirectory) {
        Instant createdAt = Instant.now();
        String fileName = FILE_DATE_FORMAT.format(createdAt) + "-media-backup.zip";
        Path root = rootDirectory();
        Path archivePath = root.resolve(fileName);

        try {
            Files.createDirectories(root);
            zipDirectory(runDirectory, archivePath);
            enforceRetention();
            long size = Files.size(archivePath);
            return new BackupArchive(toId(fileName), fileName, archivePath, size, createdAt);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create backup archive", e);
        }
    }

    public List<BackupArchive> listArchives() {
        Path root = rootDirectory();
        if (!Files.exists(root)) {
            return List.of();
        }
        try {
            return Files.list(root)
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .sorted(Comparator.comparing(this::lastModified).reversed())
                .map(this::toArchive)
                .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list archives", e);
        }
    }

    public Optional<BackupArchive> latest() {
        return listArchives().stream().findFirst();
    }

    public Optional<BackupArchive> byId(String id) {
        return listArchives().stream().filter(archive -> archive.id().equals(id)).findFirst();
    }

    public boolean deleteById(String id) {
        Optional<BackupArchive> archive = byId(id);
        if (archive.isEmpty()) {
            return false;
        }

        try {
            Files.deleteIfExists(archive.get().path());
            return true;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete archive " + id, e);
        }
    }

    public Path createRunDirectory() {
        Path runDir = rootDirectory().resolve("runs").resolve(UUID.randomUUID().toString());
        try {
            Files.createDirectories(runDir);
            return runDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create run directory", e);
        }
    }

    public void removeRunDirectory(Path runDirectory) {
        if (runDirectory == null || !Files.exists(runDirectory)) {
            return;
        }

        try (var walk = Files.walk(runDirectory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to clean up run directory", e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk run directory", e);
        }
    }

    private void enforceRetention() throws IOException {
        int retain = Math.max(1, properties.getRetentionCount());
        List<Path> archives = Files.list(rootDirectory())
            .filter(path -> path.getFileName().toString().endsWith(".zip"))
            .sorted(Comparator.comparing(this::lastModified).reversed())
            .toList();

        for (int i = retain; i < archives.size(); i++) {
            Files.deleteIfExists(archives.get(i));
        }
    }

    private void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
        Path tempZip = zipPath.resolveSibling(zipPath.getFileName() + ".tmp");
        try (OutputStream fileOut = Files.newOutputStream(tempZip, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             ZipOutputStream zipOut = new ZipOutputStream(fileOut);
             var paths = Files.walk(sourceDir)) {

            paths.filter(Files::isRegularFile).forEach(path -> {
                Path relative = sourceDir.relativize(path);
                try {
                    zipOut.putNextEntry(new ZipEntry(relative.toString()));
                    try (InputStream in = Files.newInputStream(path)) {
                        in.transferTo(zipOut);
                    }
                    zipOut.closeEntry();
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to zip file " + path, e);
                }
            });
        }
        Files.move(tempZip, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private BackupArchive toArchive(Path path) {
        String fileName = path.getFileName().toString();
        return new BackupArchive(
            toId(fileName),
            fileName,
            path,
            fileSize(path),
            lastModified(path)
        );
    }

    private String toId(String fileName) {
        return fileName.replace(".zip", "");
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
