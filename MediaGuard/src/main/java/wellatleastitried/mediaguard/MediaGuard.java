package wellatleastitried.mediaguard;

import java.util.Map;

import wellatleastitried.mediaguard.services.config.Config;
import wellatleastitried.mediaguard.services.runner.Runner;

public class MediaGuard {

    private final Configuration configuration;

    private final Runner[] activeRunners;

    public static void main(String[] args) {
        Configuration configuration = new Configuration();
        MediaGuard mediaGuard = new MediaGuard(configuration);
        mediaGuard.start();

    }

    public MediaGuard(Configuration configuration) {
        this.configuration = configuration;

        Map<String, Config> serviceConfigs = configuration.loadServiceConfigurations();
        int activeServices = serviceConfigs.size();

        activeRunners = new Runner[activeServices];

        int index = 0;
        for (Map.Entry<String, Config> entry : serviceConfigs.entrySet()) {
            String serviceName = entry.getKey();
            Config serviceConfig = entry.getValue();
            activeRunners[index++] = createRunnerForService(serviceName, serviceConfig);
        }
    }


    private Runner createRunnerForService(String serviceName, Config config) {
        switch (serviceName.toLowerCase()) {
            case "radarr":
                return new RadarrRunner(config);
            case "sonarr":
                return new SonarrRunner(config);
            case "prowlarr":
                return new ProwlarrRunner(config);
            case "jellyfin":
                return new JellyfinRunner(config);
            case "qbittorrent":
                return new QBittorrentRunner(config);
            default:
                throw new IllegalArgumentException("Unsupported service: " + serviceName);
        }
    }

    public void start() {
        while (true) {
            for (Runner runner : activeRunners) {
                if (runner != null) {
                    runner.run();
                }
            }
            try {
                Thread.sleep(configuration.backupInterval);
            } catch (InterruptedException e) {
                System.err.println("MediaGuard interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
