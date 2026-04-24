package wellatleastitried.mediaguardclient.dto;

import java.time.Duration;
import java.util.List;

public record ClientStatusDto(
    String activeServer,
    Duration pickupInterval,
    List<String> knownServers
) {
}
