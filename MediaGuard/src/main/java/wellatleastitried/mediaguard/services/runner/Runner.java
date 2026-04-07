package wellatleastitried.mediaguard.services.runner;

public abstract class Runner {
    protected String host;
    protected int port;
    protected String apiKey;

    public Runner(String host, int port, String apiKey) {
        this.host = host;
        this.port = port;
        this.apiKey = apiKey;
    }

    public abstract void run();
}
