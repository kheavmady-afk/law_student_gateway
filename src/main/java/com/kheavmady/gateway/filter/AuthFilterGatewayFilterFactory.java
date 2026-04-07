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
    private String authServiceBaseUrl;

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

            if (validator.isSecured.test(request)) {
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    logger.error("[AuthFilter] Missing Authorization header");
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing auth header"));
                }

                String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Bearer token"));
                }

                String token = authHeader.substring(7);
                // Ensure we don't double up on /validate if it's already in the base URL
                String finalUrl = authServiceBaseUrl.endsWith("/validate") 
                        ? authServiceBaseUrl + "?token=" + token 
                        : authServiceBaseUrl + "/validate?token=" + token;

                return webClientBuilder.build()
                        .get()
                        .uri(finalUrl)
                        .header("X-Gateway-Secret", gatewaySecret)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .flatMap(response -> {
                            if (response != null && Boolean.TRUE.equals(response.get("valid"))) {
                                logger.info("[AuthFilter] SUCCESS: Token valid for {}", path);
                                var mutatedRequest = request.mutate()
                                        .header("X-Gateway-Secret", gatewaySecret)
                                        .header("X-User-Role", String.valueOf(response.get("role")))
                                        .header("X-User-Name", String.valueOf(response.get("username")))
                                        .build();
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            } else {
                                logger.error("[AuthFilter] FAILURE: Auth Service rejected token");
                                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalid"));
                            }
                        })
                        .onErrorResume(e -> {
                            logger.error("[AuthFilter] ERROR: Auth service call failed: {}", e.getMessage());
                            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Auth service unreachable"));
                        });
            }

            var mutatedRequest = request.mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public static class Config {}
}
