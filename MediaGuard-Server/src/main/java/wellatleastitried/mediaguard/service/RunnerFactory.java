package wellatleastitried.mediaguard.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(RunnerFactory.class);

    public List<Runner> build(Map<String, ServiceConfig> serviceConfigMap) {
        List<Runner> runners = new ArrayList<>();

        for (Map.Entry<String, ServiceConfig> entry : serviceConfigMap.entrySet()) {
            ServiceConfig config = entry.getValue();
            if (config == null || !config.isEnabled()) {
                LOGGER.debug("Service '{}' is disabled or unconfigured — skipping", entry.getKey());
                continue;
            }

            validate(entry.getKey(), config);

            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            switch (key) {
                case "jellyfin" -> { runners.add(new JellyfinRunner(config)); LOGGER.info("Registered runner: jellyfin"); }
                case "radarr" -> { runners.add(new RadarrRunner(config)); LOGGER.info("Registered runner: radarr"); }
                case "sonarr" -> { runners.add(new SonarrRunner(config)); LOGGER.info("Registered runner: sonarr"); }
                case "prowlarr" -> { runners.add(new ProwlarrRunner(config)); LOGGER.info("Registered runner: prowlarr"); }
                case "tdarr" -> { runners.add(new TdarrRunner(config)); LOGGER.info("Registered runner: tdarr"); }
                case "qbittorrent" -> { runners.add(new QBittorrentRunner(config)); LOGGER.info("Registered runner: qbittorrent"); }
                default -> LOGGER.warn("Unknown service key '{}' — no runner registered", key);
            }
        }

        LOGGER.info("RunnerFactory built {} runner(s): {}", runners.size(), runners.stream().map(Runner::getServiceName).toList());
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
