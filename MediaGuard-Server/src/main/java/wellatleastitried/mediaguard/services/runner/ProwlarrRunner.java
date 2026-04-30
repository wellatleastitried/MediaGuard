package wellatleastitried.mediaguard.services.runner;

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
        return List.of(dir(config.getPath(), "appdata"));
    }
}
