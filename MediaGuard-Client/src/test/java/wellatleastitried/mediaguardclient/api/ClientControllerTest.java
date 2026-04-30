package wellatleastitried.mediaguardclient.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import wellatleastitried.mediaguardclient.dto.BackupDto;
import wellatleastitried.mediaguardclient.service.BackupPickupService;
import wellatleastitried.mediaguardclient.service.ClientStateService;
import wellatleastitried.mediaguardclient.service.ServerApiService;
import wellatleastitried.mediaguardclient.service.ServerDiscoveryService;

@WebMvcTest(controllers = ClientController.class)
class ClientControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ClientStateService stateService;

    @MockBean
    ServerDiscoveryService discoveryService;

    @MockBean
    ServerApiService serverApiService;

    @MockBean
    BackupPickupService backupPickupService;

    @Test
    void statusEndpointReturnsState() throws Exception {
        when(stateService.getActiveServer()).thenReturn("http://server:8080");
        when(stateService.getPickupInterval()).thenReturn(Duration.ofMinutes(45));
        when(stateService.getKnownServers()).thenReturn(List.of("http://server:8080"));

        mockMvc.perform(get("/api/client/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeServer").value("http://server:8080"));
    }

    @Test
    void discoverEndpointUpdatesState() throws Exception {
        when(discoveryService.discover()).thenReturn(List.of("http://server:8080"));
        when(stateService.getKnownServers()).thenReturn(List.of("http://server:8080"));
        when(stateService.getPickupInterval()).thenReturn(Duration.ofMinutes(30));
        when(stateService.getActiveServer()).thenReturn("http://server:8080");

        mockMvc.perform(post("/api/client/discover"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knownServers[0]").value("http://server:8080"));
    }

    @Test
    void listBackupsReturnsServerData() throws Exception {
        when(stateService.getActiveServer()).thenReturn("http://server:8080");
        when(serverApiService.listBackups("http://server:8080")).thenReturn(List.of(new BackupDto("id", "file.zip", 10L, null)));

        mockMvc.perform(get("/api/client/backups"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("id"));
    }

    @Test
    void setServerEndpointAcceptsUpdate() throws Exception {
        when(stateService.getKnownServers()).thenReturn(List.of("http://server:8080"));
        when(stateService.getPickupInterval()).thenReturn(Duration.ofMinutes(30));
        when(stateService.getActiveServer()).thenReturn("http://server:8080");

        mockMvc.perform(put("/api/client/server")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverUrl\":\"http://server:8080\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void pickupEndpointReturnsPath() throws Exception {
        when(backupPickupService.pickupLatest()).thenReturn(Path.of("/tmp/latest.zip"));

        mockMvc.perform(post("/api/client/pickup"))
            .andExpect(status().isOk());
    }
}
