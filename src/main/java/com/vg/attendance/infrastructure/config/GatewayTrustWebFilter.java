package com.vg.attendance.infrastructure.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GatewayTrustWebFilter implements WebFilter {

    private static final String INTERNAL_HEADER = "X-Internal-Request";
    private static final String INTERNAL_HEADER_VALUE = "gateway";
    public static final String TENANT_CONTEXT_KEY = "attendance.tenantId";
    public static final String USER_ID_CONTEXT_KEY = "attendance.userId";
    public static final String USER_ROLE_CONTEXT_KEY = "attendance.userRole";
    private static final Set<String> GLOBAL_ROLES = Set.of(
            "ADMIN", "DEVELOPER", "DIRECTOR", "COORDINATOR", "COORDINADOR", "SECRETARY", "SECRETARIA");

    private final String internalSecret;
    private final boolean tenantRequired;

    public GatewayTrustWebFilter(@Value("${attendance.internal-secret:}") String internalSecret,
                                 @Value("${attendance.tenant-required:false}") boolean tenantRequired) {
        this.internalSecret = internalSecret == null ? "" : internalSecret.trim();
        this.tenantRequired = tenantRequired;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (INTERNAL_HEADER_VALUE.equals(exchange.getRequest().getHeaders().getFirst(INTERNAL_HEADER))) {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
            String suppliedSecret = exchange.getRequest().getHeaders().getFirst("X-Internal-Secret");
            // El gateway omite X-Tenant-Id para roles globales (por ejemplo ADMIN).
            // Para el resto de usuarios el tenant sigue siendo obligatorio.
            if (isBlank(userId) || isBlank(role) || (isBlank(tenantId) && !isGlobalRole(role))
                    || (!internalSecret.isBlank() && !internalSecret.equals(suppliedSecret))) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority(normalizeRole(role)))
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
                    .contextWrite(context -> requestContext(context, exchange, userId, role, authentication));
        }
        if (tenantRequired && isTenantRequiredPath(exchange.getRequest().getPath().value())
                && isBlank(exchange.getRequest().getHeaders().getFirst("X-Tenant-Id"))) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange)
                .contextWrite(context -> requestContext(context, exchange, null, null, null));
    }

    private Context requestContext(Context context,
                                   ServerWebExchange exchange,
                                   String userId,
                                   String role,
                                   UsernamePasswordAuthenticationToken authentication) {
        Context updated = context;
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            updated = updated.put(TENANT_CONTEXT_KEY, tenantId);
        }
        if (userId != null && !userId.isBlank()) {
            updated = updated.put(USER_ID_CONTEXT_KEY, userId);
        }
        if (role != null && !role.isBlank()) {
            updated = updated.put(USER_ROLE_CONTEXT_KEY, role);
        }
        return authentication == null
                ? updated
                : updated.putAll(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isTenantRequiredPath(String path) {
        return path != null
                && !path.startsWith("/actuator/")
                && !path.startsWith("/v3/api-docs")
                && !path.startsWith("/swagger-ui")
                && !path.startsWith("/webjars/");
    }

    private boolean isGlobalRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return GLOBAL_ROLES.contains(normalized);
    }

    private String normalizeRole(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(java.util.Locale.ROOT);
    }
}
