package wellatleastitried.mediaguardclient;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mediaguard.client")
public class Configuration {

    private String stateFile = "./data/client/state.json";
    private String downloadDirectory = "./data/client/downloads";
    private Duration pickupInterval = Duration.ofHours(12);
    private int serverPort = 8080;
    private String preferredServerHost = "";
    private String preferredServerUrl = "";

    public String getStateFile() {
        return stateFile;
    }

    public void setStateFile(String stateFile) {
        this.stateFile = stateFile;
    }

    public String getDownloadDirectory() {
        return downloadDirectory;
    }

    public void setDownloadDirectory(String downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    public Duration getPickupInterval() {
        return pickupInterval;
    }

    public void setPickupInterval(Duration pickupInterval) {
        this.pickupInterval = pickupInterval;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getPreferredServerHost() {
        return preferredServerHost;
    }

    public void setPreferredServerHost(String preferredServerHost) {
        this.preferredServerHost = preferredServerHost;
    }

    public String getPreferredServerUrl() {
        return preferredServerUrl;
    }

    public void setPreferredServerUrl(String preferredServerUrl) {
        this.preferredServerUrl = preferredServerUrl;
    }
}
