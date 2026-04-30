package wellatleastitried.mediaguard.services.config;

public class ServiceConfig {

    private boolean enabled;
    private String path;
    private String configPath;
    private String graveyardPath;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public String getGraveyardPath() {
        return graveyardPath;
    }

    public void setGraveyardPath(String graveyardPath) {
        this.graveyardPath = graveyardPath;
    }
}
