package wellatleastitried.mediaguard.services.runner;

import wellatleastitried.mediaguard.services.config.RadarrConfig;

public class RadarrRunner extends Runner {

    public RadarrRunner(RadarrConfig config) {
        String host = config.getHost();
        int port = config.getPort();
        String apiKey = config.getApiKey();
        super(host, port, apiKey);
    }

    @Override
    public void run() {
        // Implement the logic to interact with Radarr API here
        System.out.println("Running Radarr Runner with host: " + host + ", port: " + port);
        // Example: Make API calls to Radarr using the provided host, port, and apiKey
    }
}
