package com.strangequark.fileservice.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebSecurityConfig {
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;
    @Value("${authservice.integration}")
    private boolean authserviceIntegration;
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                );
        if(authserviceIntegration) {
            httpSecurity.authorizeHttpRequests(auth -> auth
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/file/health").permitAll()
                    .requestMatchers("/api/**").hasAuthority("FILE_API_ACCESS")
                    .anyRequest().permitAll()
            );
            httpSecurity.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        } else {
            httpSecurity.authorizeHttpRequests(auth -> auth
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .anyRequest().permitAll()
            );
        }

        return httpSecurity.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {

                //Allow the reactService through the CORS policy
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "DELETE")
                        .allowedHeaders("Authorization", "Content-Type", "Range")
                        .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length", "Content-Type", "Content-Disposition")
                        .allowCredentials(true);
            }
        };
    }
}
