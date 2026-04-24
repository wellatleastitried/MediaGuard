package wellatleastitried.mediaguard.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import wellatleastitried.mediaguard.services.config.ServiceConfig;
import wellatleastitried.mediaguard.services.runner.Runner;

@ExtendWith(OutputCaptureExtension.class)
@Tag("real-runners")
class RunnersIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void runnersCreateFilesAndLogDiscoveryFromEnv(CapturedOutput output) throws Exception {
        Map<String, String> env = loadEnv();

        Map<String, ServiceConfig> configs = new LinkedHashMap<>();
        List<ServiceExpectation> expectations = new ArrayList<>();

        addJellyfin(configs, expectations, env);
        addRadarr(configs, expectations, env);
        addSonarr(configs, expectations, env);
        addProwlarr(configs, expectations, env);
        addTdarr(configs, expectations, env);
        addQbittorrent(configs, expectations, env);

        RunnerFactory factory = new RunnerFactory();
        List<Runner> runners = factory.build(configs);
        assertFalse(runners.isEmpty(), "No runners enabled in .env/.env.test.");

        Path outputDirectory = tempDir.resolve("runner-output");
        Files.createDirectories(outputDirectory);

        for (Runner runner : runners) {
            runner.run(outputDirectory);
        }

        for (ServiceExpectation expectation : expectations) {
            Path serviceOutput = outputDirectory.resolve(expectation.serviceKey());
            assertTrue(Files.exists(serviceOutput), "Missing service output directory: " + serviceOutput);

            long serviceFiles;
            try (var walk = Files.walk(serviceOutput)) {
                serviceFiles = walk.filter(Files::isRegularFile).count();
            }
            assertTrue(serviceFiles > 0, "No files copied for service " + expectation.serviceLabel());
        }

        String logs = output.getOut();
        for (ServiceExpectation expectation : expectations) {
            assertTrue(logs.contains(expectation.serviceLabel() + " runner found file:"));
        }
    }

    private void addJellyfin(Map<String, ServiceConfig> configs, List<ServiceExpectation> expectations, Map<String, String> env) {
        boolean enabled = booleanValue(env, "JELLYFIN_ENABLED", "jellyfin_enabled", false);
        if (!enabled) {
            return;
        }

        ServiceConfig config = new ServiceConfig();
        config.setEnabled(true);
        config.setConfigPath(required(env, "JELLYFIN_CONFIG_PATH", "jellyfin_config_path"));

        configs.put("jellyfin", config);
        expectations.add(new ServiceExpectation("jellyfin", "Jellyfin"));
    }

    private void addRadarr(Map<String, ServiceConfig> configs, List<ServiceExpectation> expectations, Map<String, String> env) {
        addPathService(configs, expectations, env, "radarr", "Radarr");
    }

    private void addSonarr(Map<String, ServiceConfig> configs, List<ServiceExpectation> expectations, Map<String, String> env) {
        addPathService(configs, expectations, env, "sonarr", "Sonarr");
    }

    private void addProwlarr(Map<String, ServiceConfig> configs, List<ServiceExpectation> expectations, Map<String, String> env) {
        boolean enabled = booleanValue(env, "PROWLARR_ENABLED", "prowlarr_enabled", false);
        if (!enabled) {
            return;
        }

        ServiceConfig config = new ServiceConfig();
        config.setEnabled(true);
        config.setPath(required(env, "PROWLARR_PATH", "prowlarr_path"));

        configs.put("prowlarr", config);
        expectations.add(new ServiceExpectation("prowlarr", "Prowlarr"));
    }

    private void addTdarr(Map<String, ServiceConfig> configs, List<ServiceExpectation> expectations, Map<String, String> env) {
        boolean enabled = booleanValue(env, "TDARR_ENABLED", "tdarr_enabled", false);
        if (!enabled) {
            return;
        }

        ServiceConfig config = new ServiceConfig();
        config.setEnabled(true);
        config.setPath(required(env, "TDARR_PATH", "tdarr_path"));

        configs.put("tdarr", config);
        expectations.add(new ServiceExpectation("tdarr", "Tdarr"));
    }

    private void addQbittorrent(Map<String, ServiceConfig> configs, List<ServiceExpectation> expectations, Map<String, String> env) {
        boolean enabled = booleanValue(env, "QBITTORRENT_ENABLED", "qbittorrent_enabled", false);
        if (!enabled) {
            return;
        }

        ServiceConfig config = new ServiceConfig();
        config.setEnabled(true);
        config.setPath(required(env, "QBITTORRENT_PATH", "qbittorrent_path"));
        config.setGraveyardPath(required(env, "QBITTORRENT_GRAVEYARD_PATH", "qbittorrent_graveyard_path"));

        configs.put("qbittorrent", config);
        expectations.add(new ServiceExpectation("qbittorrent", "QBittorrent"));
    }

    private void addPathService(
        Map<String, ServiceConfig> configs,
        List<ServiceExpectation> expectations,
        Map<String, String> env,
        String key,
        String label
    ) {
        String upper = key.toUpperCase(Locale.ROOT);
        boolean enabled = booleanValue(env, upper + "_ENABLED", key + "_enabled", false);
        if (!enabled) {
            return;
        }

        ServiceConfig config = new ServiceConfig();
        config.setEnabled(true);
    config.setPath(required(env, upper + "_PATH", key + "_path"));

        configs.put(key, config);
        expectations.add(new ServiceExpectation(key, label));
    }

    private Map<String, String> loadEnv() throws IOException {
        Map<String, String> merged = new HashMap<>();
        merged.putAll(parseDotEnv(resolveEnvFile()));
        merged.putAll(System.getenv());
        return merged;
    }

    private Path resolveEnvFile() {
        String override = System.getenv("MEDIAGUARD_TEST_ENV_FILE");
        if (override != null && !override.isBlank()) {
            Path explicit = Path.of(override).toAbsolutePath().normalize();
            if (Files.exists(explicit)) {
                return explicit;
            }
            throw new IllegalStateException("MEDIAGUARD_TEST_ENV_FILE not found: " + explicit);
        }

        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && current != null; i++) {
            Path testEnv = current.resolve(".env.test");
            if (Files.exists(testEnv)) {
                return testEnv;
            }
            Path prodEnv = current.resolve(".env");
            if (Files.exists(prodEnv)) {
                return prodEnv;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("No .env.test or .env found. Create one from .env.test.example or .env.example.");
    }

    private Map<String, String> parseDotEnv(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        List<String> lines = Files.readAllLines(file);
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    private String required(Map<String, String> env, String... keys) {
        String value = firstPresent(env, keys);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required .env key. Expected one of: " + String.join(", ", keys));
        }
        return value;
    }

    private boolean booleanValue(Map<String, String> env, String keyUpper, String keyLower, boolean fallback) {
        String value = firstPresent(env, keyUpper, keyLower);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("y") || normalized.equals("on");
    }

    private String firstPresent(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String value = env.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record ServiceExpectation(String serviceKey, String serviceLabel) {
    }
}
