package wellatleastitried.mediaguard.services.runner;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLocalCopyRunner implements Runner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLocalCopyRunner.class);

    private final String serviceName;

    protected AbstractLocalCopyRunner(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public void run(Path outputDirectory) {
        Path serviceDirectory = outputDirectory.resolve(serviceName.toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(serviceDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create output directory for " + serviceName, e);
        }

        List<CopySpec> specs = copySpecs();
        LOGGER.info("[{}] Starting copy: {} spec(s) to {}", serviceName, specs.size(), serviceDirectory);
        int succeeded = 0;
        for (CopySpec spec : specs) {
            try {
                copySpec(spec, serviceDirectory);
                succeeded++;
            } catch (RuntimeException ex) {
                LOGGER.warn("[{}] Failed spec '{}' (source={}). Continuing.", serviceName, spec.targetName(), spec.sourcePath(), ex);
            }
        }
        LOGGER.info("[{}] Copy complete: {}/{} specs succeeded", serviceName, succeeded, specs.size());
    }

    protected abstract List<CopySpec> copySpecs();

    protected CopySpec dir(String sourcePath, String targetName) {
        return new CopySpec(sourcePath, targetName, true);
    }

    protected CopySpec file(String sourcePath, String targetName) {
        return new CopySpec(sourcePath, targetName, false);
    }

    private void copySpec(CopySpec spec, Path serviceDirectory) {
        if (spec.sourcePath() == null || spec.sourcePath().isBlank()) {
            throw new IllegalArgumentException(serviceName + " runner missing source path for " + spec.targetName());
        }

        Path source = Path.of(spec.sourcePath()).toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            throw new IllegalStateException(serviceName + " source path not found: " + source);
        }

        Path target = serviceDirectory.resolve(spec.targetName());

        try {
            if (spec.directory()) {
                if (!Files.isDirectory(source)) {
                    throw new IllegalStateException(serviceName + " expected directory path: " + source);
                }
                copyDirectory(source, target);
            } else {
                if (!Files.isRegularFile(source)) {
                    throw new IllegalStateException(serviceName + " expected file path: " + source);
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                LOGGER.debug("[{}] Copied file: {}", serviceName, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException(serviceName + " runner failed while copying " + source, e);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination);
                LOGGER.debug("[{}] Created dir: {}", serviceName, destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative);
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                LOGGER.debug("[{}] Copied: {}", serviceName, destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                LOGGER.warn("[{}] Failed to read: {}. Continuing.", serviceName, file, exc);
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(source, visitor);
    }

    protected record CopySpec(String sourcePath, String targetName, boolean directory) {
    }
}
