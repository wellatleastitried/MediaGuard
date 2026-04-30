package wellatleastitried.mediaguard;

import java.util.List;

public final class Constants {

    public static final String JELLYFIN = "Jellyfin";
    public static final String RADARR = "Radarr";
    public static final String SONARR = "Sonarr";
    public static final String PROWLARR = "Prowlarr";
    public static final String TDARR = "Tdarr";
    public static final String QBITTORRENT = "QBittorrent";

    public static final List<String> SUPPORTED_SERVICES = List.of(
        JELLYFIN,
        RADARR,
        SONARR,
        PROWLARR,
        TDARR,
        QBITTORRENT
    );

    private Constants() {
    }
}
