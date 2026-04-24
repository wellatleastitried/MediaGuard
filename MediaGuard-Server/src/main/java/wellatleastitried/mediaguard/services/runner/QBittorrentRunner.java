package wellatleastitried.mediaguard.services.runner;

import java.nio.file.Path;
import java.util.List;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

public class QBittorrentRunner extends AbstractLocalCopyRunner {

    private final ServiceConfig config;

    public QBittorrentRunner(ServiceConfig config) {
        super("QBittorrent");
        this.config = config;
    }

    @Override
    protected List<CopySpec> copySpecs() {
        Path basePath = Path.of(config.getPath());
        return List.of(
            dir(basePath.resolve("utils").toString(), "utils"),
            dir(basePath.resolve("config").toString(), "config"),
            dir(config.getGraveyardPath(), "graveyard"),
            file(basePath.resolve("docker-compose.yml").toString(), "docker-compose.yml")
        );
    }
}
