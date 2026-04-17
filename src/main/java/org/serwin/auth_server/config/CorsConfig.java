package org.serwin.auth_server.config;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS is now handled by SecurityConfig.java to avoid duplicate headers
// This configuration is disabled to prevent duplicate Access-Control-Allow-Origin headers
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Disabled - CORS configured in SecurityConfig
    }
}




