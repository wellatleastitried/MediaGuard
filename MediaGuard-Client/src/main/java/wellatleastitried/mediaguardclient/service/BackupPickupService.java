package wellatleastitried.mediaguardclient.service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wellatleastitried.mediaguardclient.Configuration;

@Service
public class BackupPickupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupPickupService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Configuration configuration;
    private final ClientStateService clientStateService;
    private final ServerApiService serverApiService;
    private Instant nextPickup;

    public BackupPickupService(
        Configuration configuration,
        ClientStateService clientStateService,
        ServerApiService serverApiService
    ) {
        this.configuration = configuration;
        this.clientStateService = clientStateService;
        this.serverApiService = serverApiService;
        this.nextPickup = Instant.now().plus(clientStateService.getPickupInterval());
    }

    @Scheduled(fixedDelay = 60000)
    public void scheduledPickup() {
        Instant now = Instant.now();
        if (now.isBefore(nextPickup)) {
            return;
        }
        LOGGER.info("Scheduled pickup triggered (next after: {})", nextPickup);
        try {
            Path result = pickupLatest();
            LOGGER.info("Scheduled pickup complete: {}", result);
        } catch (RuntimeException ex) {
            LOGGER.warn("Scheduled pickup failed", ex);
        }
        nextPickup = now.plus(clientStateService.getPickupInterval());
        LOGGER.info("Next pickup scheduled at: {}", nextPickup);
    }

    public Path pickupLatest() {
        String activeServer = clientStateService.getActiveServer();
        if (activeServer == null || activeServer.isBlank()) {
            LOGGER.error("Pickup requested but no active server is configured");
            throw new IllegalStateException("No active server selected");
        }

        String fileName = DATE_FORMAT.format(Instant.now()) + "-media-backup.zip";
        Path destination = Path.of(configuration.getDownloadDirectory()).toAbsolutePath().normalize().resolve(fileName);
        LOGGER.info("Downloading latest backup from {} to {}", activeServer, destination);
        return serverApiService.downloadLatest(activeServer, destination);
    }

    public Duration updatePickupInterval(Duration interval) {
        Duration updated = clientStateService.setPickupInterval(interval);
        nextPickup = Instant.now().plus(updated);
        return updated;
    }
}
