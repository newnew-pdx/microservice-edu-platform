package com.dyl.edu.gateway.filter;

import com.dyl.edu.common.jwt.JwtProperties;
import com.dyl.edu.common.jwt.JwtUserInfo;
import com.dyl.edu.common.jwt.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 全局 JWT 鉴权过滤器。
 *
 * <p>外部请求先进入 Gateway，由这里统一校验 token。校验通过后，
 * Gateway 会把 token 中可信的用户信息转换成 X-User-* 请求头传给后端服务。</p>
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

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
        // 登录接口和健康检查不需要 token，否则用户还没登录就无法获取 token。
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // 统一要求 Authorization: Bearer <token> 格式，格式不对直接拒绝。
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            log.warn("JWT 鉴权失败，请求缺少合法 Authorization 请求头，path={}", path);
            return unauthorized(exchange);
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            JwtUserInfo userInfo = JwtUtil.parseToken(token, jwtProperties.getSecret());
            log.info("JWT 鉴权成功，path={}, userId={}, username={}",
                    path, userInfo.getUserId(), userInfo.getUsername());
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> {
                        // 先删除客户端传入的同名请求头，防止用户伪造身份或角色。
                        headers.remove(USER_ID_HEADER);
                        headers.remove(USER_NAME_HEADER);
                        headers.remove(USER_ROLE_HEADER);
                        // 再写入 Gateway 从 JWT 中解析出的可信用户上下文。
                        headers.set(USER_ID_HEADER, String.valueOf(userInfo.getUserId()));
                        headers.set(USER_NAME_HEADER, userInfo.getUsername());
                        headers.set(USER_ROLE_HEADER, userInfo.getRole());
                    })
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            // token 解析失败、签名不正确或已过期时，统一按未认证处理。
            log.warn("JWT 鉴权失败，token 解析或校验异常，path={}, error={}", path, ex.getClass().getSimpleName());
            return unauthorized(exchange);
        }
    }

    @Override
    public int getOrder() {
        // 让鉴权过滤器尽量靠前执行，先确认身份再进入后续路由过滤链。
        return -100;
    }

    /**
     * 当前 Step1 的白名单路径。
     */
    private boolean isWhitePath(String path) {
        return "/user/login".equals(path) || "/health".equals(path);
    }

    /**
     * 未认证响应。当前只返回 HTTP 401，不额外输出响应体，保持 Gateway 简单。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
