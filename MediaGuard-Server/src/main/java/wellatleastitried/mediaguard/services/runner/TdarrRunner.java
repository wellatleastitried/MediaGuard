package wellatleastitried.mediaguard.services.runner;

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
        return List.of(dir(config.getPath(), "appdata"));
    }
}
