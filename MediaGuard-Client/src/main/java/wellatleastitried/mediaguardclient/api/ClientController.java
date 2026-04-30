package wellatleastitried.mediaguardclient.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguardclient.Configuration;
import wellatleastitried.mediaguardclient.dto.BackupDto;
import wellatleastitried.mediaguardclient.dto.ClientStatusDto;
import wellatleastitried.mediaguardclient.dto.ConnectionStatusDto;
import wellatleastitried.mediaguardclient.dto.IntervalUpdateRequest;
import wellatleastitried.mediaguardclient.dto.ServerSelectionRequest;
import wellatleastitried.mediaguardclient.dto.ServerStatusDto;
import wellatleastitried.mediaguardclient.service.BackupPickupService;
import wellatleastitried.mediaguardclient.service.ClientStateService;
import wellatleastitried.mediaguardclient.service.ServerApiService;
import wellatleastitried.mediaguardclient.service.ServerDiscoveryService;

@RestController
@RequestMapping("/api/client")
public class ClientController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientController.class);

    private final Configuration configuration;
    private final ClientStateService stateService;
    private final ServerDiscoveryService discoveryService;
    private final ServerApiService serverApiService;
    private final BackupPickupService pickupService;

    public ClientController(
        Configuration configuration,
        ClientStateService stateService,
        ServerDiscoveryService discoveryService,
        ServerApiService serverApiService,
        BackupPickupService pickupService
    ) {
        this.configuration = configuration;
        this.stateService = stateService;
        this.discoveryService = discoveryService;
        this.serverApiService = serverApiService;
        this.pickupService = pickupService;
    }

    @GetMapping("/status")
    public ClientStatusDto status() {
        LOGGER.info("GET /api/client/status");
        String activeServer = stateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            String preferredIp = configuration.getPreferredServerIp();
            if (preferredIp != null && !preferredIp.isBlank()) {
                LOGGER.info("No active server — probing preferred IP: {}", preferredIp);
                discoveryService.resolveServerByIp(preferredIp).ifPresent(url -> {
                    LOGGER.info("Auto-resolved server from preferred IP: {}", url);
                    stateService.setActiveServer(url);
                });
            }
        }

        ClientStatusDto dto = new ClientStatusDto(
            stateService.getActiveServer(),
            stateService.getPickupInterval(),
            stateService.getKnownServers()
        );
        LOGGER.info("Status: activeServer={}, pickupInterval={}, knownServers={}", dto.activeServer(), dto.pickupInterval(), dto.knownServers());
        return dto;
    }

    @PostMapping("/discover")
    public ClientStatusDto discover() {
        LOGGER.info("POST /api/client/discover — starting network scan");
        List<String> discovered = discoveryService.discover();
        LOGGER.info("Discovery complete — found {} server(s): {}", discovered.size(), discovered);
        stateService.updateKnownServers(discovered);
        return status();
    }

    @GetMapping("/server-health")
    public ConnectionStatusDto serverHealth() {
        LOGGER.info("GET /api/client/server-health");
        String activeServer = stateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            LOGGER.warn("server-health: no active server configured");
            return new ConnectionStatusDto("", false);
        }

        try {
            ServerStatusDto serverStatus = serverApiService.health(activeServer);
            LOGGER.info("server-health: {} is reachable", activeServer);
            return new ConnectionStatusDto(activeServer, true, serverStatus);
        } catch (RuntimeException ex) {
            LOGGER.warn("server-health: {} is unreachable — {}", activeServer, ex.getMessage());
            return new ConnectionStatusDto(activeServer, false);
        }
    }

    @PutMapping("/server")
    public ClientStatusDto setServer(@RequestBody ServerSelectionRequest request) {
        LOGGER.info("PUT /api/client/server — request={}", request);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String requestedUrl = request.serverUrl();
        if (requestedUrl != null && !requestedUrl.isBlank()) {
            LOGGER.info("Setting active server by URL: {}", requestedUrl.trim());
            stateService.setActiveServer(requestedUrl.trim());
            return status();
        }

        String serverIp = request.serverIp();
        if (serverIp == null || serverIp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either serverUrl or serverIp must be provided");
        }

        Integer serverPort = request.serverPort();
        if (serverPort != null && (serverPort < 1 || serverPort > 65535)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serverPort must be between 1 and 65535");
        }

        LOGGER.info("Resolving server by IP={}, port={}", serverIp, serverPort);
        String resolved = discoveryService.resolveServerByIp(serverIp, serverPort)
            .orElseThrow(() -> {
                LOGGER.warn("Unable to resolve server at IP={}, port={}", serverIp, serverPort);
                return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to resolve MediaGuard server on provided IP");
            });
        LOGGER.info("Resolved server: {}", resolved);
        stateService.setActiveServer(resolved);
        return status();
    }

    @GetMapping("/backups")
    public List<BackupDto> listBackups() {
        String activeServer = requireActiveServer();
        LOGGER.info("GET /api/client/backups — forwarding to {}", activeServer);
        List<BackupDto> backups = serverApiService.listBackups(activeServer);
        LOGGER.info("Received {} backup(s) from server", backups.size());
        return backups;
    }

    @PostMapping("/backups/run")
    public void runBackup() {
        String activeServer = requireActiveServer();
        LOGGER.info("POST /api/client/backups/run — triggering backup on {}", activeServer);
        serverApiService.triggerBackup(activeServer);
        LOGGER.info("Backup triggered successfully on {}", activeServer);
    }

    @PostMapping("/pickup")
    public String pickupNow() {
        LOGGER.info("POST /api/client/pickup — manual pickup requested");
        Path file = pickupService.pickupLatest();
        LOGGER.info("Pickup complete — saved to: {}", file);
        return file.toString();
    }

    @PutMapping("/pickup-interval")
    public ClientStatusDto updatePickupInterval(@RequestBody IntervalUpdateRequest request) {
        LOGGER.info("PUT /api/client/pickup-interval — requested={}", request.interval());
        Duration duration = pickupService.updatePickupInterval(request.interval());
        LOGGER.info("Pickup interval updated to: {}", duration);
        return new ClientStatusDto(
            stateService.getActiveServer(),
            duration,
            stateService.getKnownServers()
        );
    }

    @PutMapping("/server-schedule")
    public void updateServerSchedule(@RequestBody IntervalUpdateRequest request) {
        String activeServer = requireActiveServer();
        LOGGER.info("PUT /api/client/server-schedule — interval={}, target={}", request.interval(), activeServer);
        serverApiService.updateServerSchedule(activeServer, request.interval());
        LOGGER.info("Server schedule updated on {}", activeServer);
    }

    private String requireActiveServer() {
        String activeServer = stateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            LOGGER.warn("Request rejected — no active server configured");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active MediaGuard server selected");
        }
        return activeServer;
    }
}
