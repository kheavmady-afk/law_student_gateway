package com.kheavmady.gateway.filter;

import com.kheavmady.gateway.config.RouteValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final RouteValidator validator;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.gateway-secret}")
    private String gatewaySecret;

    public AuthenticationFilter(RouteValidator validator, WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.validator = validator;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var request = exchange.getRequest();

            // Inject secret for internal communication
            var mutatedRequest = request.mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();

            if (validator.isSecured.test(request)) {
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing auth header");
                }

                String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                String token = authHeader.substring(7);

                return webClientBuilder.build()
                        .get()
                        .uri("http://localhost:8081/auth/validate?token=" + token)
                        .header("X-Gateway-Secret", gatewaySecret) // Prove identity to Auth Service
                        .retrieve()
                        .bodyToMono(Map.class)
                        .flatMap(response -> {
                            if (response != null && (Boolean) response.get("valid")) {
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            } else {
                                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
                            }
                        });
            }
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    public static class Config {}
}