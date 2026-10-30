package ma.enset.adria.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class GatewayRoutes {
    @Bean
    public RouteLocator bankRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("accounts", r -> r.path("/api/accounts", "/api/accounts/**").uri("lb://account-service"))
                .route("transactions", r -> r.path("/api/transactions", "/api/transactions/**").uri("lb://transaction-service"))
                .route("assistant", r -> r.path("/api/chat", "/api/chat/**").uri("lb://assistant-service"))
                .route("accounts-graphql", r -> r.path("/graphql/accounts").filters(f -> f.setPath("/graphql")).uri("lb://account-service"))
                .route("transactions-graphql", r -> r.path("/graphql/transactions").filters(f -> f.setPath("/graphql")).uri("lb://transaction-service"))
                .route("accounts-health", r -> r.path("/services/accounts/health").filters(f -> f.setPath("/actuator/health")).uri("lb://account-service"))
                .route("transactions-health", r -> r.path("/services/transactions/health").filters(f -> f.setPath("/actuator/health")).uri("lb://transaction-service"))
                .route("assistant-health", r -> r.path("/services/assistant/health").filters(f -> f.setPath("/actuator/health")).uri("lb://assistant-service"))
                .build();
    }

    @Bean
    public CorsWebFilter localClientCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }
}
