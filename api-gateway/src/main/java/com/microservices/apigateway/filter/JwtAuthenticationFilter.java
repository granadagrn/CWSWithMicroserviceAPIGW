package com.microservices.apigateway.filter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtAuthenticationFilter implements GlobalFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final WebClient webClient;
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>(); // Store refreshed tokens

    @Value("${keycloak.realm.public-key}")
    private String keycloakPublicKey;

    @Value("${keycloak.token-endpoint}")
    private String keycloakTokenEndpoint;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    public JwtAuthenticationFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("Incoming request: {}", exchange.getRequest().getURI());

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.info("No Bearer token found, proceeding without authentication.");
            return chain.filter(exchange);
        }

        String accessToken = authHeader.substring(7);
        // Check if we already have a refreshed token in memory
        if (tokenCache.containsKey(accessToken)) {
            String refreshedToken = tokenCache.get(accessToken);
            logger.info("Using refreshed token from cache.");
            return forwardRequestWithNewToken(exchange, chain, refreshedToken);
        }

        return validateToken(accessToken)
                .flatMap(isValid -> {
                    if (isValid) {
                        return chain.filter(exchange);
                    } else {
                        return handleTokenExpiration(exchange, chain, accessToken);
                    }
                })
                .onErrorResume(e -> {
                    logger.error("Error during token validation: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private PublicKey getPublicKey(String publicKeyStr) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(publicKeyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private Mono<Boolean> validateToken(String token) {
        logger.info("Started validateToken method in JwtAuthenticationFilter...");
        return Mono.fromCallable(() -> {
            try {
                String publicKeyStr = keycloakPublicKey;  // Replace with actual public key
                PublicKey publicKey = getPublicKey(publicKeyStr);

                Jwts.parserBuilder()
                        .setSigningKey(publicKey)
                        .build()
                        .parseClaimsJws(token);
                return true;
            } catch (ExpiredJwtException e) {
                logger.info("Token expired: {}", e.getMessage());
                return false;
            } catch (Exception e) {
                logger.error("Token validation error: {}", e.getMessage());
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> handleTokenExpiration(ServerWebExchange exchange, GatewayFilterChain chain, String oldAccessToken) {
        logger.info("Handling expired token, attempting refresh...");
        String refreshToken = exchange.getRequest().getHeaders().getFirst("Refresh-token");

        if (refreshToken == null) {
            logger.info("No refresh token provided, returning 401.");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return refreshAccessToken(refreshToken)
                .flatMap(newAccessToken -> {
                    logger.info("New access token obtained: {}", newAccessToken);

                    // Store the new token in cache
                    tokenCache.put(oldAccessToken, newAccessToken);
                    return forwardRequestWithNewToken(exchange, chain, newAccessToken);
                })
                .onErrorResume(e -> {
                    logger.error("Error refreshing token: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }

    private Mono<String> refreshAccessToken(String refreshToken) {
        logger.info("Refreshing access token...");
        String keycloakTokenUrl = keycloakTokenEndpoint;

        return webClient.post()
                .uri(keycloakTokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("access_token"));
    }

    private Mono<Void> forwardRequestWithNewToken(ServerWebExchange exchange, GatewayFilterChain chain, String newToken) {
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                .build();

        ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
        return chain.filter(newExchange);
    }
}