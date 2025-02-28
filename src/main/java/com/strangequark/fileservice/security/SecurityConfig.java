package com.strangequark.fileservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                boolean dockerDeployment = Boolean.parseBoolean(System.getenv("DOCKER_DEPLOYMENT"));

                //Allow the reactService through the CORS policy
                registry.addMapping("/**")
                        .allowedOrigins(
                                "http://react-service:3001", "http://localhost:3001",
                                "http://gateway-service:8080", "http://localhost:8080"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowCredentials(true);
            }
        };
    }
}
