package wellatleastitried.mediaguardclient;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/css/**", "/js/**", "/images/**", "/*.png", "/*.ico", "/*.svg")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(0);

        registry
            .addResourceHandler("/index.html")
            .addResourceLocations("classpath:/static/index.html")
            .setCachePeriod(0);
    }
}
