package wellatleastitried.mediaguard.services.runner;

import java.nio.file.Path;
import java.util.List;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

public class SonarrRunner extends AbstractLocalCopyRunner {

    private final ServiceConfig config;

    public SonarrRunner(ServiceConfig config) {
        super("Sonarr");
        this.config = config;
    }

    @Override
    protected List<CopySpec> copySpecs() {
        Path basePath = Path.of(config.getPath());
        return List.of(
            dir(basePath.resolve("data").toString(), "data"),
            dir(basePath.resolve("config").toString(), "config"),
            file(basePath.resolve("docker-compose.yml").toString(), "docker-compose.yml")
        );
    }
}
