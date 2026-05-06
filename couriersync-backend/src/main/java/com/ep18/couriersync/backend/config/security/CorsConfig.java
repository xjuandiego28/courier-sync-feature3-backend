package com.ep18.couriersync.backend.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
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

        applyOrigins(
                registry.addMapping("/graphql")
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders(CorsConfiguration.ALL)
                        .exposedHeaders("Content-Type")
                        .maxAge(3600),
                origins
        ).allowCredentials(true);

        applyOrigins(
                registry.addMapping("/graphiql/**")
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders(CorsConfiguration.ALL)
                        .maxAge(3600),
                origins
        ).allowCredentials(true);
    }

    private CorsRegistration applyOrigins(CorsRegistration registration, CorsOrigins origins) {
        applyOriginValues(origins.exact(), registration::allowedOrigins);
        applyOriginValues(origins.patterns(), registration::allowedOriginPatterns);
        return registration;
    }

    private void applyOriginValues(List<String> values, OriginConfigurer configurer) {
        if (!values.isEmpty()) {
            configurer.apply(values.toArray(String[]::new));
        }
    }

    private record CorsOrigins(List<String> exact, List<String> patterns) {

        private static CorsOrigins from(String csv) {
            List<String> origins = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(Predicate.not(String::isEmpty))
                    .toList();

            return new CorsOrigins(
                    origins.stream()
                            .filter(Predicate.not(CorsOrigins::isPattern))
                            .toList(),

                    origins.stream()
                            .filter(CorsOrigins::isPattern)
                            .toList()
            );
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