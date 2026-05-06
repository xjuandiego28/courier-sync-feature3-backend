package com.ep18.couriersync.backend.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * CORS para /graphql y /graphiql.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,https://courier-sync-feature3-frontend.vercel.app}")
    private String allowedOriginsCsv;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsOrigins origins = CorsOrigins.from(allowedOriginsCsv);

        applyOrigins(registry.addMapping("/graphql")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(CorsConfiguration.ALL)
                .exposedHeaders("Content-Type")
                .maxAge(3600), origins)
                .allowCredentials(true);

        applyOrigins(registry.addMapping("/graphiql/**")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders(CorsConfiguration.ALL)
                .maxAge(3600), origins)
                .allowCredentials(true);
    }

    private CorsRegistration applyOrigins(CorsRegistration registration, CorsOrigins origins) {
        applyOriginValues(origins.exact(), registration::allowedOrigins);
        applyOriginValues(origins.patterns(), registration::allowedOriginPatterns);
        return registration;
    }

    private void applyOriginValues(String[] values, OriginConfigurer configurer) {
        Optional.of(values)
                .filter(origins -> origins.length > 0)
                .ifPresent(configurer::apply);
    }

    private record CorsOrigins(String[] exact, String[] patterns) {

        private static CorsOrigins from(String csv) {
            List<String> origins = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .toList();
            return new CorsOrigins(
                    origins.stream().filter(Predicate.not(CorsOrigins::isPattern)).toArray(String[]::new),
                    origins.stream().filter(CorsOrigins::isPattern).toArray(String[]::new));
        }

        private static boolean isPattern(String origin) {
            return origin.contains("*");
        }
    }

    @FunctionalInterface
    private interface OriginConfigurer {
        CorsRegistration apply(String... origins);
    }
}
