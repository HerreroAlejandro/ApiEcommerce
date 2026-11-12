package com.api.crud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig {

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins:}") String configuredOrigins) {
        Set<String> origins = new LinkedHashSet<>();
        if (!configuredOrigins.isBlank()) {
            for (String entry : configuredOrigins.split(",", -1)) {
                String origin = entry.trim();
                if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
                URI uri;
                try {
                    uri = URI.create(origin);
                } catch (IllegalArgumentException exception) {
                    throw invalidOrigins();
                }
                if (!("http".equalsIgnoreCase(uri.getScheme())
                                || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null
                        || uri.getUserInfo() != null
                        || uri.getRawQuery() != null
                        || uri.getRawFragment() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || uri.getPort() == 0
                        || uri.getPort() > 65535) throw invalidOrigins();
                origins.add(origin);
            }
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(origins));
        configuration.setAllowedMethods(
                List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "X-Download-Token"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private IllegalArgumentException invalidOrigins() {
        return new IllegalArgumentException(
                "CORS_ALLOWED_ORIGINS debe contener origenes HTTP/HTTPS exactos separados por"
                    + " comas, sin rutas ni comodines");
    }
}
