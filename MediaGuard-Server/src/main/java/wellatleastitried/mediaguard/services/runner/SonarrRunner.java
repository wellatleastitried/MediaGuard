package wellatleastitried.mediaguard.services.runner;

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
        return List.of(dir(config.getPath(), "appdata"));
    }
}
