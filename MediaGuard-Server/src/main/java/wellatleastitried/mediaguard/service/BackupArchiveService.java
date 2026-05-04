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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguard.MediaGuardProperties;
import wellatleastitried.mediaguard.model.BackupArchive;

@Service
public class BackupArchiveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupArchiveService.class);

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneOffset.UTC);
    private static final long ARCHIVE_CACHE_TTL_MS = 60_000;  // Cache archive list for 60 seconds

    private final MediaGuardProperties properties;
    private final AtomicReference<List<BackupArchive>> cachedArchives = new AtomicReference<>(null);
    private final AtomicLong cacheLastUpdatedMs = new AtomicLong(0);

    public BackupArchiveService(MediaGuardProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void cleanupStaleArtifactsOnStartup() {
        Path root = rootDirectory();
        Path runsRoot = root.resolve("runs");

        try {
            Files.createDirectories(root);

            try (var stream = Files.list(root)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".zip.tmp"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            LOGGER.info("Startup cleanup: removed stale temporary archive {}", path.getFileName());
                        } catch (IOException e) {
                            LOGGER.warn("Startup cleanup: failed to remove stale temporary archive {}", path, e);
                        }
                    });
            }

            if (Files.exists(runsRoot)) {
                try (var stream = Files.list(runsRoot)) {
                    stream
                        .filter(Files::isDirectory)
                        .forEach(runDir -> {
                            try {
                                removeRunDirectory(runDir);
                                LOGGER.info("Startup cleanup: removed stale run directory {}", runDir.getFileName());
                            } catch (RuntimeException e) {
                                LOGGER.warn("Startup cleanup: failed to remove stale run directory {}", runDir, e);
                            }
                        });
                }
            }

            enforceRetention();
            LOGGER.info("Startup cleanup: retention policy enforced");
        } catch (IOException e) {
            LOGGER.warn("Startup cleanup: unable to initialize backup root {}", root, e);
        } catch (RuntimeException e) {
            LOGGER.warn("Startup cleanup: failed while enforcing retention policy", e);
        }
    }

    public Path rootDirectory() {
        return Path.of(properties.getBackupRoot()).toAbsolutePath().normalize();
    }

    public BackupArchive createArchive(Path runDirectory) {
        Instant createdAt = Instant.now();
        String fileName = FILE_DATE_FORMAT.format(createdAt) + "-media-backup.zip";
        Path root = rootDirectory();
        Path archivePath = root.resolve(fileName);

        LOGGER.info("Creating backup archive: {} from runDir={}", fileName, runDirectory);
        try {
            Files.createDirectories(root);
            zipDirectory(runDirectory, archivePath);
            enforceRetention();
            long size = Files.size(archivePath);
            LOGGER.info("Archive created: {} ({} bytes)", fileName, size);

            // Invalidate cache after new archive created
            cacheLastUpdatedMs.set(0);
            cachedArchives.set(null);

            return new BackupArchive(toId(fileName), fileName, archivePath, size, createdAt);
        } catch (IOException e) {
            LOGGER.error("Failed to create backup archive: {}", fileName, e);
            throw new IllegalStateException("Failed to create backup archive", e);
        }
    }

    public List<BackupArchive> listArchives() {
        long now = System.currentTimeMillis();
        long lastUpdate = cacheLastUpdatedMs.get();

        if (now - lastUpdate < ARCHIVE_CACHE_TTL_MS) {
            List<BackupArchive> cached = cachedArchives.get();
            if (cached != null) {
                LOGGER.debug("Returning cached archive list ({}ms old)", now - lastUpdate);
                return cached;
            }
        }

        // Cache miss -> rebuild list
        Path root = rootDirectory();
        List<BackupArchive> archives;
        if (!Files.exists(root)) {
            archives = List.of();
        } else {
            try (var stream = Files.list(root)) {
                archives = stream
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .map(this::toArchive)
                    .toList();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to list archives", e);
            }
        }

        cachedArchives.set(archives);
        cacheLastUpdatedMs.set(now);
        LOGGER.debug("Rebuilt archive cache with {} archives", archives.size());
        return archives;
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
            LOGGER.info("Archive deleted: {}", id);
            cacheLastUpdatedMs.set(0);

            cachedArchives.set(null);
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to delete archive: {}", id, e);
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
        List<Path> archives;
        try (var stream = Files.list(rootDirectory())) {
            archives = stream
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .sorted(Comparator.comparing(this::lastModified).reversed())
                .toList();
        }
        for (int i = retain; i < archives.size(); i++) {
            Path toDelete = archives.get(i);
            LOGGER.info("Retention: deleting old archive: {}", toDelete.getFileName());
            Files.deleteIfExists(toDelete);
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
