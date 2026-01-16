package me.imrashb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = false)
public class SwaggerConfig implements WebMvcConfigurer {

    @Value("${ENVIRONMENT:production}")
    private String environment;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Only register Swagger resources if enabled and not in production
        if (!"production".equals(environment)) {
            registry.addResourceHandler("/swagger-ui/**")
                    .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc-openapi-ui/");
            registry.addResourceHandler("/webjars/**")
                    .addResourceLocations("classpath:/META-INF/resources/webjars/");
        }
    }
}
