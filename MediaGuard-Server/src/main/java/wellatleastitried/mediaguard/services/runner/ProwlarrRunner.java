package wellatleastitried.mediaguard.services.runner;

import java.nio.file.Path;
import java.util.List;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

public class ProwlarrRunner extends AbstractLocalCopyRunner {

    private final ServiceConfig config;

    public ProwlarrRunner(ServiceConfig config) {
        super("Prowlarr");
        this.config = config;
    }

    @Override
    protected List<CopySpec> copySpecs() {
        Path basePath = Path.of(config.getPath());
        return List.of(
            dir(basePath.resolve("config").toString(), "config"),
            file(basePath.resolve("docker-compose.yml").toString(), "docker-compose.yml")
        );
    }
}
