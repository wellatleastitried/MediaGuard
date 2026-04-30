package wellatleastitried.mediaguard.services.runner;

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
        return List.of(
            dir(config.getPath(), "appdata"),
            dir(config.getGraveyardPath(), "graveyard")
        );
    }
}
