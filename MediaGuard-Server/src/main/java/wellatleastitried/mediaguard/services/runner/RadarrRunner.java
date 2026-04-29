package wellatleastitried.mediaguard.services.runner;

import java.util.List;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

public class RadarrRunner extends AbstractLocalCopyRunner {

    private final ServiceConfig config;

    public RadarrRunner(ServiceConfig config) {
        super("Radarr");
        this.config = config;
    }

    @Override
    protected List<CopySpec> copySpecs() {
        return List.of(dir(config.getPath(), "appdata"));
    }
}
