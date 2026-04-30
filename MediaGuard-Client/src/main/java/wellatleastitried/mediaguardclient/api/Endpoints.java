package wellatleastitried.mediaguardclient.api;

public enum Endpoints {

    CLIENT_STATUS("/api/client/status"),
    CLIENT_DISCOVER("/api/client/discover"),
    CLIENT_SERVER("/api/client/server"),
    CLIENT_BACKUPS("/api/client/backups"),
    CLIENT_BACKUP_RUN("/api/client/backups/run"),
    CLIENT_PICKUP("/api/client/pickup"),
    CLIENT_PICKUP_INTERVAL("/api/client/pickup-interval"),
    CLIENT_SERVER_SCHEDULE("/api/client/server-schedule"),
    SERVER_HEALTH("/api/v1/health"),
    SERVER_BACKUPS("/api/v1/backups"),
    SERVER_BACKUP_RUN("/api/v1/backups/run"),
    SERVER_BACKUP_LATEST_DOWNLOAD("/api/v1/backups/latest/download"),
    SERVER_SCHEDULE("/api/v1/schedule");

    private final String path;

    Endpoints(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
