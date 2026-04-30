package wellatleastitried.mediaguard.services.runner;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

import java.util.List;

public class JellyfinRunner extends AbstractLocalCopyRunner {

    private final ServiceConfig config;

    public JellyfinRunner(ServiceConfig config) {
        super("Jellyfin");
        this.config = config;
    }

    @Override
    protected List<CopySpec> copySpecs() {
        return List.of(dir(config.getConfigPath(), "config"));
    }
}
