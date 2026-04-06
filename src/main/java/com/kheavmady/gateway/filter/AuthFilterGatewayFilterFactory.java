package com.kheavmady.gateway.filter;

import com.kheavmady.gateway.config.RouteValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

@Component
public class AuthFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<AuthFilterGatewayFilterFactory.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthFilterGatewayFilterFactory.class);
    private final RouteValidator validator;

    @Value("${internal.gateway-secret}")
    private String gatewaySecret;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public AuthFilterGatewayFilterFactory(RouteValidator validator) {
        super(Config.class);
        this.validator = validator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var request = exchange.getRequest();
            String path = request.getURI().getPath();

            logger.info("[AuthFilter] Processing request: {} {}", request.getMethod(), path);

            if (validator.isSecured.test(request)) {
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    logger.error("[AuthFilter] Rejecting: Missing Authorization header for {}", path);
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing auth header"));
                }

                String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);

                if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() < 8) {
                    logger.error("[AuthFilter] Rejecting: Invalid Authorization format for {}", path);
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Bearer token"));
                }

                String token = authHeader.substring(7);

                try {
                    // Local Validation
                    Claims claims = Jwts.parser()
                            .verifyWith(getSignKey())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                    
                    logger.info("[AuthFilter] SUCCESS: Token valid for user: {}", claims.getSubject());

                } catch (Exception e) {
                    logger.error("[AuthFilter] FAILURE: Token validation failed: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalid or expired"));
                }
            }

            // Inject secret and continue (Zero-Trust)
            var mutatedRequest = request.mutate()
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public static class Config {}
}
