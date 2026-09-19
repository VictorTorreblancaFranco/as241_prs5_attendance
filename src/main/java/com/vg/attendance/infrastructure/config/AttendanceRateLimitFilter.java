package com.vg.attendance.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AttendanceRateLimitFilter implements WebFilter {

    private final Map<String, RequestBucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${attendance.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${attendance.rate-limit.requests-per-minute:120}")
    private int requestsPerMinute;

    @Autowired
    public AttendanceRateLimitFilter() {
        this(Clock.systemUTC());
    }

    AttendanceRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!enabled || !path.startsWith("/api/attendance")) {
            return chain.filter(exchange);
        }

        String key = clientKey(exchange);
        long currentMinute = currentMinute();
        removeExpiredBuckets(currentMinute);
        RequestBucket bucket = buckets.computeIfAbsent(key, ignored -> new RequestBucket(currentMinute));

        if (!bucket.tryConsume(currentMinute, requestsPerMinute)) {
            log.warn("Rate limit exceeded for key={} path={}", key, path);
            byte[] body = """
                    {"code":"RATE_LIMIT_EXCEEDED","message":"Demasiadas solicitudes. Intenta nuevamente en unos segundos.","status":429}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                    .bufferFactory()
                    .wrap(body)));
        }

        return chain.filter(exchange);
    }

    private long currentMinute() {
        return clock.millis() / 60_000;
    }

    private String clientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private void removeExpiredBuckets(long currentMinute) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(currentMinute));
    }

    private static final class RequestBucket {
        private volatile long minute;
        private final AtomicInteger count = new AtomicInteger();

        private RequestBucket(long minute) {
            this.minute = minute;
        }

        private synchronized boolean tryConsume(long currentMinute, int limit) {
            if (currentMinute != minute) {
                minute = currentMinute;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }

        private boolean isExpired(long currentMinute) {
            return currentMinute - minute > 5;
        }
    }
}
