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
public class AuthFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthFilterGatewayFilterFactory.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthFilterGatewayFilterFactory.class);
    private final RouteValidator validator;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.gateway-secret}")
    private String gatewaySecret;

    @Value("${internal.auth-service-url}")
    private String authServiceUrl;

    public AuthFilterGatewayFilterFactory(RouteValidator validator, WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.validator = validator;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var request = exchange.getRequest();
            String path = request.getURI().getPath();

            // Log entry
            logger.info("[AuthFilter] Processing request: {} {}", request.getMethod(), path);

            if (validator.isSecured.test(request)) {
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    logger.error("[AuthFilter] Rejecting: Missing Authorization header for {}", path);
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing auth header"));
                }

                String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);

                // SAFETY CHECK: Ensure header is long enough and starts with Bearer
                if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() < 8) {
                    logger.error("[AuthFilter] Rejecting: Invalid Authorization format for {}", path);
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Bearer token"));
                }

                String token = authHeader.substring(7);
                String validationUrl = authServiceUrl + "/validate?token=" + token;

                logger.info("[AuthFilter] Calling Auth Service: {}", validationUrl);

                return webClientBuilder.build()
                        .get()
                        .uri(validationUrl)
                        .header("X-Gateway-Secret", gatewaySecret)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .flatMap(response -> {
                            if (response != null && Boolean.TRUE.equals(response.get("valid"))) {
                                logger.info("[AuthFilter] SUCCESS: Token valid for {}", path);

                                // Inject secret and continue
                                var mutatedRequest = request.mutate()
                                        .header("X-Gateway-Secret", gatewaySecret)
                                        .build();
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            } else {
                                logger.error("[AuthFilter] FAILURE: Auth Service rejected token for {}", path);
                                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalid"));
                            }
                        })
                        .onErrorResume(e -> {
                            logger.error("[AuthFilter] ERROR: Authentication process failed: {}", e.getMessage());
                            if (e instanceof ResponseStatusException) return Mono.error(e);
                            return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Authentication Service Error"));
                        });
            }

            // Public endpoint: Just inject secret and pass through
            var mutatedRequest = request.mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public static class Config {}
}