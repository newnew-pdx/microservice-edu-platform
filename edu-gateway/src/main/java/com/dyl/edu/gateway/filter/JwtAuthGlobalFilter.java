package com.dyl.edu.gateway.filter;

import com.dyl.edu.common.jwt.JwtProperties;
import com.dyl.edu.common.jwt.JwtUserInfo;
import com.dyl.edu.common.jwt.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_NAME_HEADER = "X-User-Name";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtProperties jwtProperties;

    public JwtAuthGlobalFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            JwtUserInfo userInfo = JwtUtil.parseToken(token, jwtProperties.getSecret());
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(USER_ID_HEADER);
                        headers.remove(USER_NAME_HEADER);
                        headers.remove(USER_ROLE_HEADER);
                        headers.set(USER_ID_HEADER, String.valueOf(userInfo.getUserId()));
                        headers.set(USER_NAME_HEADER, userInfo.getUsername());
                        headers.set(USER_ROLE_HEADER, userInfo.getRole());
                    })
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitePath(String path) {
        return "/user/login".equals(path) || "/health".equals(path);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
