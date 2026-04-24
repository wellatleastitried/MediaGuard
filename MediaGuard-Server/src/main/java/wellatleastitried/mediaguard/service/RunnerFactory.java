package wellatleastitried.mediaguard.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import wellatleastitried.mediaguard.Constants;
import wellatleastitried.mediaguard.services.config.ServiceConfig;
import wellatleastitried.mediaguard.services.runner.JellyfinRunner;
import wellatleastitried.mediaguard.services.runner.ProwlarrRunner;
import wellatleastitried.mediaguard.services.runner.QBittorrentRunner;
import wellatleastitried.mediaguard.services.runner.RadarrRunner;
import wellatleastitried.mediaguard.services.runner.Runner;
import wellatleastitried.mediaguard.services.runner.SonarrRunner;
import wellatleastitried.mediaguard.services.runner.TdarrRunner;

@Component
public class RunnerFactory {

    public List<Runner> build(Map<String, ServiceConfig> serviceConfigMap) {
        List<Runner> runners = new ArrayList<>();

        for (Map.Entry<String, ServiceConfig> entry : serviceConfigMap.entrySet()) {
            ServiceConfig config = entry.getValue();
            if (config == null || !config.isEnabled()) {
                continue;
            }

            validate(entry.getKey(), config);

            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            switch (key) {
                case "jellyfin" -> runners.add(new JellyfinRunner(config));
                case "radarr" -> runners.add(new RadarrRunner(config));
                case "sonarr" -> runners.add(new SonarrRunner(config));
                case "prowlarr" -> runners.add(new ProwlarrRunner(config));
                case "tdarr" -> runners.add(new TdarrRunner(config));
                case "qbittorrent" -> runners.add(new QBittorrentRunner(config));
                default -> {
                }
            }
        }

        return runners;
    }

    private void validate(String serviceName, ServiceConfig config) {
        boolean supported = Constants.SUPPORTED_SERVICES.stream().anyMatch(value -> value.equalsIgnoreCase(serviceName));
        if (!supported) {
            throw new IllegalArgumentException("Unsupported service configured: " + serviceName);
        }

        String key = serviceName.toLowerCase(java.util.Locale.ROOT);
        switch (key) {
            case "jellyfin" -> requirePath(serviceName, "configPath", config.getConfigPath());
            case "radarr", "sonarr", "prowlarr", "tdarr" -> requirePath(serviceName, "path", config.getPath());
            case "qbittorrent" -> {
                requirePath(serviceName, "path", config.getPath());
                requirePath(serviceName, "graveyardPath", config.getGraveyardPath());
            }
            default -> {
            }
        }
    }

    private void requirePath(String serviceName, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Service " + serviceName + " requires " + fieldName);
        }
    }
}
