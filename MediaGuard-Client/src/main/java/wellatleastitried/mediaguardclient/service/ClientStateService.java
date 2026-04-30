package wellatleastitried.mediaguardclient.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import wellatleastitried.mediaguardclient.Configuration;

@Service
public class ClientStateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientStateService.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ClientState {
        public String activeServer = "";
        public long pickupIntervalSeconds = 43_200;
        public List<String> knownServers = new ArrayList<>();
    }

    private final Configuration configuration;
    private final ObjectMapper mapper;
    private final List<String> knownServers = new ArrayList<>();
    private String activeServer;
    private Duration pickupInterval;

    public ClientStateService(Configuration configuration, ObjectMapper mapper) {
        this.configuration = configuration;
        this.mapper = mapper;
        this.pickupInterval = safe(configuration.getPickupInterval());
        this.activeServer = null;
        load();
        LOGGER.info("ClientStateService initialized: activeServer={}, pickupInterval={}, knownServers={}",
            activeServer, pickupInterval, knownServers);
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
            LOGGER.info("Auto-selected first known server: {}", activeServer);
        }
        LOGGER.info("Known servers updated: {}", knownServers);
        save();
    }

    public synchronized void setActiveServer(String serverUrl) {
        LOGGER.info("Active server changed: {} -> {}", activeServer, serverUrl);
        this.activeServer = serverUrl;
        if (serverUrl != null && !serverUrl.isBlank() && !knownServers.contains(serverUrl)) {
            knownServers.add(serverUrl);
        }
        save();
    }

    public synchronized Duration setPickupInterval(Duration interval) {
        this.pickupInterval = safe(interval);
        LOGGER.info("Pickup interval updated to: {}", this.pickupInterval);
        save();
        return this.pickupInterval;
    }

    private void load() {
        Path path = statePath();
        if (!Files.exists(path)) {
            LOGGER.info("No state file found at {}, starting fresh", path);
            return;
        }
        try {
            ClientState state = mapper.readValue(path.toFile(), ClientState.class);
            activeServer = state.activeServer;
            pickupInterval = Duration.ofSeconds(state.pickupIntervalSeconds);
            knownServers.clear();
            knownServers.addAll(state.knownServers);
            if ((activeServer == null || activeServer.isBlank()) && !knownServers.isEmpty()) {
                activeServer = knownServers.getFirst();
            }
            LOGGER.info("State loaded from {}: activeServer={}, pickupInterval={}, knownServers={}",
                path, activeServer, pickupInterval, knownServers);
        } catch (IOException e) {
            LOGGER.warn("Failed to load state from {}, starting with defaults", path, e);
        }
    }

    private void save() {
        Path path = statePath();
        try {
            Files.createDirectories(path.getParent());
            ClientState state = new ClientState();
            state.activeServer = activeServer == null ? "" : activeServer;
            state.pickupIntervalSeconds = pickupInterval.toSeconds();
            state.knownServers = List.copyOf(knownServers);
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
            LOGGER.debug("Client state persisted to {}", path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist client state", e);
        }
    }

    private Path statePath() {
        return Path.of(configuration.getStateFile()).toAbsolutePath().normalize();
    }

    private Duration safe(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return Duration.ofHours(12);
        }
        return duration;
    }
}
