package com.microservices.apigateway.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)  // Disable CSRF for APIs
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/customers/**").authenticated()  // Protect /customers endpoint
                        .pathMatchers("/addresses/**").authenticated()  // Protect /addresses endpoint
                        .anyExchange().permitAll()  // Allow everything else
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {})) // ✅ FIXED: Use new JWT syntax
                .build(); // ✅ FIXED: Ensure `ServerHttpSecurity` is correctly used
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("*")); // Allow all origins
        corsConfig.setAllowedMethods(List.of("*")); // Allow all HTTP methods
        corsConfig.setAllowedHeaders(List.of("*")); // Allow all headers
        corsConfig.setExposedHeaders(List.of("Authorization")); // Expose Authorization header

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}