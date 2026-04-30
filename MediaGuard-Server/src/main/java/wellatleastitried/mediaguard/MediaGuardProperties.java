package wellatleastitried.mediaguard;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import wellatleastitried.mediaguard.services.config.ServiceConfig;

@ConfigurationProperties(prefix = "mediaguard")
public class MediaGuardProperties {

    private Duration backupInterval = Duration.ofHours(12);
    private String backupRoot = "./data/server/backups";
    private int retentionCount = 10;
    private Map<String, ServiceConfig> services = new HashMap<>();

    public Duration getBackupInterval() {
        return backupInterval;
    }

    public void setBackupInterval(Duration backupInterval) {
        this.backupInterval = backupInterval;
    }

    public String getBackupRoot() {
        return backupRoot;
    }

    public void setBackupRoot(String backupRoot) {
        this.backupRoot = backupRoot;
    }

    public int getRetentionCount() {
        return retentionCount;
    }

    public void setRetentionCount(int retentionCount) {
        this.retentionCount = retentionCount;
    }

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    public void setServices(Map<String, ServiceConfig> services) {
        this.services = services;
    }
}
