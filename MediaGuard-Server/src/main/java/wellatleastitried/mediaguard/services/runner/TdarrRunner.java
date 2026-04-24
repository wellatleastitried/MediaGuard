package wellatleastitried.mediaguard.services.runner;

import java.nio.file.Path;
import java.util.List;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

public class TdarrRunner extends AbstractLocalCopyRunner {

    private final ServiceConfig config;

    public TdarrRunner(ServiceConfig config) {
        super("Tdarr");
        this.config = config;
    }

    @Override
    protected List<CopySpec> copySpecs() {
        Path basePath = Path.of(config.getPath());
        return List.of(
            dir(basePath.resolve("configs").toString(), "configs"),
            dir(basePath.resolve("server").toString(), "server"),
            dir(basePath.resolve("logs").toString(), "logs"),
            file(basePath.resolve("docker-compose.yml").toString(), "docker-compose.yml")
        );
    }
}
