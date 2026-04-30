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

import wellatleastitried.mediaguardclient.Configuration;
import wellatleastitried.mediaguardclient.dto.BackupDto;
import wellatleastitried.mediaguardclient.dto.ClientStatusDto;
import wellatleastitried.mediaguardclient.dto.IntervalUpdateRequest;
import wellatleastitried.mediaguardclient.dto.ServerSelectionRequest;
import wellatleastitried.mediaguardclient.service.BackupPickupService;
import wellatleastitried.mediaguardclient.service.ClientStateService;
import wellatleastitried.mediaguardclient.service.ServerApiService;
import wellatleastitried.mediaguardclient.service.ServerDiscoveryService;

@RestController
@RequestMapping("/api/client")
public class ClientController {

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
        String activeServer = stateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            String preferredHost = configuration.getPreferredServerHost();
            if (preferredHost != null && !preferredHost.isBlank()) {
                discoveryService.resolveServerByIp(preferredHost).ifPresent(stateService::setActiveServer);
            }
        }

        return new ClientStatusDto(
            stateService.getActiveServer(),
            stateService.getPickupInterval(),
            stateService.getKnownServers()
        );
    }

    @PostMapping("/discover")
    public ClientStatusDto discover() {
        List<String> discovered = discoveryService.discover();
        stateService.updateKnownServers(discovered);
        return status();
    }

    @PutMapping("/server")
    public ClientStatusDto setServer(@RequestBody ServerSelectionRequest request) {
        String requestedUrl = request.serverUrl();
        if (requestedUrl != null && !requestedUrl.isBlank()) {
            stateService.setActiveServer(requestedUrl.trim());
            return status();
        }

        String serverIp = request.serverIp();
        if (serverIp == null || serverIp.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Either serverUrl or serverIp must be provided");
        }

        String resolved = discoveryService.resolveServerByIp(serverIp)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to resolve MediaGuard server on provided IP"));
        stateService.setActiveServer(resolved);
        return status();
    }

    @GetMapping("/backups")
    public List<BackupDto> listBackups() {
        String activeServer = requireActiveServer();
        return serverApiService.listBackups(activeServer);
    }

    @PostMapping("/backups/run")
    public void runBackup() {
        String activeServer = requireActiveServer();
        serverApiService.triggerBackup(activeServer);
    }

    @PostMapping("/pickup")
    public String pickupNow() {
        Path file = pickupService.pickupLatest();
        return file.toString();
    }

    @PutMapping("/pickup-interval")
    public ClientStatusDto updatePickupInterval(@RequestBody IntervalUpdateRequest request) {
        Duration duration = pickupService.updatePickupInterval(request.interval());
        return new ClientStatusDto(
            stateService.getActiveServer(),
            duration,
            stateService.getKnownServers()
        );
    }

    @PutMapping("/server-schedule")
    public void updateServerSchedule(@RequestBody IntervalUpdateRequest request) {
        String activeServer = requireActiveServer();
        serverApiService.updateServerSchedule(activeServer, request.interval());
    }

    private String requireActiveServer() {
        String activeServer = stateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active MediaGuard server selected");
        }
        return activeServer;
    }
}
