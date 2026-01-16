package me.imrashb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:https://horairets.emmanuelcoulombe.dev}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Split comma-separated origins and remove whitespace
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                   HttpServletResponse response,
                                   Object handler) throws Exception {
                // Swagger endpoints are now enabled by default
                // They can be disabled via SPRINGDOC_SWAGGER_UI_ENABLED=false
                return true;
            }

            @Override
            public void postHandle(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler,
                                 ModelAndView modelAndView) {
                // Security headers
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("X-XSS-Protection", "1; mode=block");
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            }
        });
    }


}
