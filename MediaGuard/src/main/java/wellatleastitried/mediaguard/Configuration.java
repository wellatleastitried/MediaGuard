package wellatleastitried.mediaguard;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import wellatleastitried.mediaguard.services.config.Config;

public class Configuration {

    // Change this to be the actual path to the config file
    private String configFilePath = "config.properties";

    private final Properties systemProperties;

    private final boolean usingRadarr;
    private final boolean usingSonarr;
    private final boolean usingProwlarr;
    private final boolean usingJellyfin;
    private final boolean usingQBittorrent;

    public final int backupInterval;

    public Configuration() {
        File file = new File(configFilePath);
        if (!file.exists()) {
            System.err.println("Configuration file not found, please check the provided path: " + configFilePath);
            System.exit(1);
        }

        systemProperties = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            systemProperties.load(fis);
            /*
             * Properties file should be in the format:
             * radarr=true
             * sonarr=false
            */
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
            System.exit(1);
        }

        usingRadarr = Boolean.parseBoolean(systemProperties.getProperty("usingRadarr", "false"));
        usingSonarr = Boolean.parseBoolean(systemProperties.getProperty("usingSonarr", "false"));
        usingProwlarr = Boolean.parseBoolean(systemProperties.getProperty("usingProwlarr", "false"));
        usingJellyfin = Boolean.parseBoolean(systemProperties.getProperty("usingJellyfin", "false"));
        usingQBittorrent = Boolean.parseBoolean(systemProperties.getProperty("usingQBittorrent", "false"));

        String intervalFormatted = systemProperties.getProperty("backupInterval", "86400000"); // Default to 24 hours
        backupInterval = getInterval(intervalFormatted);
    }

    private int getInterval(String intervalFormatted) {
        if (intervalFormatted.endsWith("s")) {
            return Integer.parseInt(intervalFormatted.replace("s", "")) * 1000;
        } else if (intervalFormatted.endsWith("m")) {
            return Integer.parseInt(intervalFormatted.replace("m", "")) * 60 * 1000;
        } else if (intervalFormatted.endsWith("h")) {
            return Integer.parseInt(intervalFormatted.replace("h", "")) * 60 * 60 * 1000;
        } else {
            return Integer.parseInt(intervalFormatted);
        }
    }

    public Map<String, Config> loadServiceConfigurations() {
        Map<String, Config> serviceConfigs = new HashMap<>();

        if (usingRadarr) {
            serviceConfigs.put("radarr", loadServiceConfig("radarr"));
        }
        if (usingSonarr) {
            serviceConfigs.put("sonarr", loadServiceConfig("sonarr"));
        }
        if (usingProwlarr) {
            serviceConfigs.put("prowlarr", loadServiceConfig("prowlarr"));
        }
        if (usingJellyfin) {
            serviceConfigs.put("jellyfin", loadServiceConfig("jellyfin"));
        }
        if (usingQBittorrent) {
            serviceConfigs.put("qbittorrent", loadServiceConfig("qbittorrent"));
        }

        return serviceConfigs;
    }

    private Config loadServiceConfig(String serviceName) {
        String host = systemProperties.getProperty(serviceName + ".host", "localhost");
        int port = Integer.parseInt(systemProperties.getProperty(serviceName + ".port", "80"));
        String apiKey = systemProperties.getProperty(serviceName + ".apiKey", "");

        Config config;

        switch (serviceName.toLowerCase()) {
            case "radarr":
                return new wellatleastitried.mediaguard.services.config.RadarrConfig(host, port, apiKey);
            case "sonarr":
                return new wellatleastitried.mediaguard.services.config.SonarrConfig(host, port, apiKey);
            case "prowlarr":
                return new wellatleastitried.mediaguard.services.config.ProwlarrConfig(host, port, apiKey);
            case "jellyfin":
                return new wellatleastitried.mediaguard.services.config.JellyfinConfig(host, port, apiKey);
            case "qbittorrent":
                return new wellatleastitried.mediaguard.services.config.QBittorrentConfig(host, port, apiKey);
            default:
                throw new IllegalArgumentException("Unsupported service: " + serviceName);
        }

        return config;
    }

}
