package wellatleastitried.mediaguardclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(Configuration.class)
public class MediaGuard {

    public static void main(String[] args) {
        SpringApplication.run(MediaGuard.class, args);
    }
}
