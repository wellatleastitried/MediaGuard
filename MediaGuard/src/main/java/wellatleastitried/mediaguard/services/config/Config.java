package wellatleastitried.mediaguard.services.config;

public abstract class Config {
    protected String host;
    protected int port;
    protected String apiKey;
    
    public Config(String host, int port, String apiKey) {
        this.host = host;
        this.port = port;
        this.apiKey = apiKey;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getApiKey() {
        return apiKey;
    }
}
