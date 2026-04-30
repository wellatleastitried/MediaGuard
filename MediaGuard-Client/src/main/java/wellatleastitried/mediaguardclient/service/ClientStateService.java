package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import wellatleastitried.mediaguardclient.Configuration;

@Service
public class ClientStateService {

    private static final Pattern ACTIVE_SERVER_PATTERN = Pattern.compile("\"activeServer\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PICKUP_INTERVAL_PATTERN = Pattern.compile("\"pickupIntervalSeconds\"\\s*:\\s*(\\d+)");

    private final Configuration configuration;
    private final List<String> knownServers = new ArrayList<>();
    private String activeServer;
    private Duration pickupInterval;

    public ClientStateService(Configuration configuration) {
        this.configuration = configuration;
        this.pickupInterval = safe(configuration.getPickupInterval());
        this.activeServer = null;
        load();
    }

    public synchronized String getActiveServer() {
        return activeServer;
    }

    public synchronized Duration getPickupInterval() {
        return pickupInterval;
    }

    public synchronized List<String> getKnownServers() {
        return List.copyOf(knownServers);
    }

    public synchronized void updateKnownServers(List<String> servers) {
        knownServers.clear();
        knownServers.addAll(servers.stream().distinct().toList());
        if ((activeServer == null || activeServer.isBlank()) && !knownServers.isEmpty()) {
            activeServer = knownServers.getFirst();
        }
        save();
    }

    public synchronized void setActiveServer(String serverUrl) {
        this.activeServer = serverUrl;
        if (serverUrl != null && !serverUrl.isBlank() && !knownServers.contains(serverUrl)) {
            knownServers.add(serverUrl);
        }
        save();
    }

    public synchronized Duration setPickupInterval(Duration interval) {
        this.pickupInterval = safe(interval);
        save();
        return this.pickupInterval;
    }

    private void load() {
        Path path = statePath();
        if (!Files.exists(path)) {
            return;
        }

        try {
            String json = Files.readString(path);
            Matcher activeMatcher = ACTIVE_SERVER_PATTERN.matcher(json);
            if (activeMatcher.find()) {
                activeServer = activeMatcher.group(1);
            }
            Matcher intervalMatcher = PICKUP_INTERVAL_PATTERN.matcher(json);
            if (intervalMatcher.find()) {
                pickupInterval = Duration.ofSeconds(Long.parseLong(intervalMatcher.group(1)));
            }

            knownServers.clear();
            parseKnownServers(json).forEach(knownServers::add);
            if ((activeServer == null || activeServer.isBlank()) && !knownServers.isEmpty()) {
                activeServer = knownServers.getFirst();
            }
        } catch (IOException ignored) {
        }
    }

    private void save() {
        Path path = statePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist client state", e);
        }
    }

    private Path statePath() {
        return Path.of(configuration.getStateFile()).toAbsolutePath().normalize();
    }

    private String toJson() {
        String servers = knownServers.stream().map(value -> "\"" + value + "\"").reduce((a, b) -> a + "," + b).orElse("");
        return "{"
            + "\n  \"activeServer\": \"" + nullSafe(activeServer) + "\"," 
            + "\n  \"pickupIntervalSeconds\": " + pickupInterval.toSeconds() + ","
            + "\n  \"knownServers\": [" + servers + "]"
            + "\n}";
    }

    private List<String> parseKnownServers(String json) {
        int start = json.indexOf("\"knownServers\"");
        if (start < 0) {
            return List.of();
        }
        int arrayStart = json.indexOf('[', start);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0 || arrayEnd <= arrayStart) {
            return List.of();
        }

        String body = json.substring(arrayStart + 1, arrayEnd).trim();
        if (body.isBlank()) {
            return List.of();
        }

        String[] values = body.split(",");
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String trimmed = value.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
                result.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return result;
    }

    private Duration safe(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return Duration.ofHours(12);
        }
        return duration;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

}
