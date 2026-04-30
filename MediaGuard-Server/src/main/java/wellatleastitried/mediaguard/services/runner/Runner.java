package wellatleastitried.mediaguard.services.runner;

import java.nio.file.Path;

public interface Runner {

    String getServiceName();

    void run(Path outputDirectory);
}
