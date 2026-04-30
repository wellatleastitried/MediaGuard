package wellatleastitried.mediaguard.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BackupControllerIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("mediaguard.backup-root", () -> tempDir.resolve("archives").toString());
        registry.add("mediaguard.services.jellyfin.enabled", () -> false);
        registry.add("mediaguard.services.radarr.enabled", () -> false);
        registry.add("mediaguard.services.sonarr.enabled", () -> false);
        registry.add("mediaguard.services.prowlarr.enabled", () -> false);
        registry.add("mediaguard.services.tdarr.enabled", () -> false);
        registry.add("mediaguard.services.qbittorrent.enabled", () -> false);
    }

    @BeforeEach
    void setup() throws Exception {
        Files.createDirectories(tempDir.resolve("archives"));
    }

    @Test
    void healthEndpointWorks() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.healthy").value(true));
    }

    @Test
    void runAndListBackups() throws Exception {
        mockMvc.perform(post("/api/v1/backups/run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archive.id").isNotEmpty());

        mockMvc.perform(get("/api/v1/backups"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }
}
