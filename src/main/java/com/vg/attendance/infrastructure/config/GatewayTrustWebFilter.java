package com.vg.attendance.infrastructure.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

public class GatewayTrustWebFilter implements WebFilter {

    private static final String INTERNAL_HEADER = "X-Internal-Request";
    private static final String INTERNAL_HEADER_VALUE = "gateway";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (INTERNAL_HEADER_VALUE.equals(exchange.getRequest().getHeaders().getFirst(INTERNAL_HEADER))) {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId != null ? userId : "gateway",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "GATEWAY")))
            );
            // Igual que vg-ms-schedule: al confiar en la identidad resuelta por
            // el gateway se elimina el Authorization entrante para que el
            // resource server no intente validar un token ajeno (Firebase u
            // otro issuer) y termine en 401. Las llamadas MS→MS reenvían el
            // token con WebClients propios (headers internos spoofeados).
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> headers.remove("Authorization"))
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        }
        return chain.filter(exchange);
    }
}
