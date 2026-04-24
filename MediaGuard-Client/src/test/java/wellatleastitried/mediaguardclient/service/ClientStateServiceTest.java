package wellatleastitried.mediaguardclient.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import wellatleastitried.mediaguardclient.Configuration;

class ClientStateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndLoadsState() {
        Configuration cfg = new Configuration();
        cfg.setStateFile(tempDir.resolve("state.json").toString());
        cfg.setPickupInterval(Duration.ofHours(12));

        ClientStateService state = new ClientStateService(cfg);
        state.updateKnownServers(List.of("http://a:8080", "http://b:8080"));
        state.setActiveServer("http://b:8080");
        state.setPickupInterval(Duration.ofMinutes(30));

        ClientStateService reloaded = new ClientStateService(cfg);
        assertEquals("http://b:8080", reloaded.getActiveServer());
        assertEquals(Duration.ofMinutes(30), reloaded.getPickupInterval());
        assertEquals(2, reloaded.getKnownServers().size());
    }
}
