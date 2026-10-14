package pe.edu.vallegrande.edunova.attendance.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestObservabilityFilter implements WebFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = Optional.ofNullable(request.getHeaders().getFirst(TRACE_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        long startNanos = System.nanoTime();

        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        MDC.put("traceId", traceId);
        log.info("request.start traceId={} method={} path={}",
                traceId,
                request.getMethod(),
                request.getPath().pathWithinApplication().value());
        MDC.clear();

        return chain.filter(exchange)
                .doOnSuccess(unused -> record(exchange, traceId, startNanos, null))
                .doOnError(error -> record(exchange, traceId, startNanos, error));
    }

    private void record(ServerWebExchange exchange, String traceId, long startNanos, Throwable error) {
        int status = exchange.getResponse().getStatusCode() == null
                ? error == null ? 200 : 500
                : exchange.getResponse().getStatusCode().value();
        long elapsedNanos = System.nanoTime() - startNanos;
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String method = exchange.getRequest().getMethod().name();

        Timer.builder("attendance.http.server.requests")
                .description("HTTP requests handled by attendance service")
                .tag("method", method)
                .tag("path", path)
                .tag("status", String.valueOf(status))
                .register(meterRegistry)
                .record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        MDC.put("traceId", traceId);
        if (error == null) {
            log.info("request.end traceId={} method={} path={} status={} durationMs={}",
                    traceId, method, path, status, elapsedNanos / 1_000_000);
        } else {
            log.warn("request.error traceId={} method={} path={} status={} durationMs={} error={}",
                    traceId, method, path, status, elapsedNanos / 1_000_000, error.getClass().getSimpleName());
        }
        MDC.clear();
    }
}
