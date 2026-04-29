package wellatleastitried.mediaguard.services.runner;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
            LOGGER.info("{} runner found directory: {}", serviceName, serviceDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create output directory for " + serviceName, e);
        }

        for (CopySpec spec : copySpecs()) {
            copySpec(spec, serviceDirectory);
        }
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
                LOGGER.info("{} runner found file: {}", serviceName, target);
            }
        } catch (IOException e) {
            throw new IllegalStateException(serviceName + " runner failed while copying " + source, e);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source, FileVisitOption.FOLLOW_LINKS)) {
            for (Path path : walk.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                    LOGGER.info("{} runner found directory: {}", serviceName, destination);
                    continue;
                }
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                LOGGER.info("{} runner found file: {}", serviceName, destination);
            }
        }
    }

    protected record CopySpec(String sourcePath, String targetName, boolean directory) {
    }
}
