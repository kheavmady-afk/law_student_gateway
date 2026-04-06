package com.kheavmady.gateway.filter;

import com.kheavmady.gateway.config.RouteValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
    private final RouteValidator validator;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.gateway-secret}")
    private String gatewaySecret;

    @Value("${internal.auth-service-url}")
    private String authServiceUrl;

    public AuthenticationFilter(RouteValidator validator, WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.validator = validator;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var request = exchange.getRequest();
            logger.info("Incoming request: {} {}", request.getMethod(), request.getURI().getPath());

            // 1. STRIP incoming internal headers and inject the gateway secret
            var mutatedRequest = request.mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();

            if (validator.isSecured.test(request)) {
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    logger.warn("Missing Authorization header for secured route: {}", request.getURI().getPath());
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing auth header"));
                }

                String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                if (!authHeader.startsWith("Bearer ")) {
                    logger.warn("Invalid Authorization header format");
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid auth header"));
                }
                
                String token = authHeader.substring(7);
                String validationUrl = authServiceUrl + "/validate?token=" + token;
                
                logger.info("Validating token with Auth Service at: {}", validationUrl);

                return webClientBuilder.build()
                        .get()
                        .uri(validationUrl)
                        .header("X-Gateway-Secret", gatewaySecret)
                        .retrieve()
                        .onStatus(status -> status.isError(), clientResponse -> {
                            return clientResponse.bodyToMono(Map.class)
                                    .flatMap(errorBody -> {
                                        logger.error("Auth Service returned error: {}", errorBody);
                                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token validation failed"));
                                    });
                        })
                        .bodyToMono(Map.class)
                        .flatMap(response -> {
                            if (response != null && (Boolean) response.get("valid")) {
                                logger.info("Token validated successfully for {}", request.getURI().getPath());
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            } else {
                                logger.warn("Auth Service returned invalid token response");
                                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
                            }
                        })
                        .onErrorResume(e -> {
                            logger.error("Error during authentication: {}", e.getMessage());
                            if (e instanceof ResponseStatusException) return Mono.error(e);
                            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Auth Service unreachable: " + e.getMessage()));
                        });
            }
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public static class Config {}
}