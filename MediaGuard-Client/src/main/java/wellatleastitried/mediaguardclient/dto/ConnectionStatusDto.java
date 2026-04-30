package wellatleastitried.mediaguardclient.dto;

public record ConnectionStatusDto(String activeServer, boolean connected, ServerStatusDto serverStatus) {
    public ConnectionStatusDto(String activeServer, boolean connected) {
        this(activeServer, connected, null);
    }
}
