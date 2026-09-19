package com.vg.attendance.infrastructure.adapter.out.client;

import com.vg.attendance.infrastructure.adapter.out.client.dto.EnrollmentResponse;
import com.vg.attendance.domain.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EnrollmentClient {

    private final WebClient enrollmentWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    @Value("${attendance.clients.timeout:5s}")
    private Duration requestTimeout = Duration.ofSeconds(5);

    // ── Caché en memoria del roster (sin BD) ────────────────────────────────
    // El roster activo por año/grado/sección cambia poco durante el día: se
    // consulta enrollment la primera vez y se reutiliza 10 minutos. Evita
    // golpear enrollment en cada carga de clase y hace a asistencia resiliente
    // a caídas breves de ese servicio mientras el roster esté cacheado.
    private record RosterCacheEntry(List<EnrollmentResponse> students, long storedAt) {}

    private static final long ROSTER_TTL_MS = 10 * 60 * 1000L; // 10 minutos
    private final Map<String, RosterCacheEntry> rosterCache = new ConcurrentHashMap<>();

    @Autowired
    public EnrollmentClient(@Qualifier("enrollmentWebClient") WebClient enrollmentWebClient,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            RetryRegistry retryRegistry) {
        this.enrollmentWebClient = enrollmentWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("enrollmentService");
        this.retry = retryRegistry.retry("enrollmentService");
    }

    public Flux<EnrollmentResponse> getStudentsByGradeSectionYear(Long gradeId, Long sectionId, Long yearId, String token) {
        log.info("Obteniendo alumnos para grado: {}, sección: {}, año: {}", gradeId, sectionId, yearId);
        String key = rosterKey(gradeId, sectionId, yearId);

        RosterCacheEntry cached = rosterCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.storedAt() < ROSTER_TTL_MS) {
            log.info("Roster servido desde caché para grado {}, sección {}, año {} ({} alumnos)",
                    gradeId, sectionId, yearId, cached.students().size());
            return Flux.fromIterable(cached.students());
        }

        return enrollmentWebClient.get()
                .uri("/api/enrollments/grade/{gradeId}/section/{sectionId}/year/{yearId}", gradeId, sectionId, yearId)
                .retrieve()
                .bodyToFlux(EnrollmentResponse.class)
                .collectList()
                .doOnNext(students -> {
                    rosterCache.put(key, new RosterCacheEntry(List.copyOf(students), System.currentTimeMillis()));
                    log.info("Roster cacheado para grado {}, sección {}, año {}: {} alumnos",
                            gradeId, sectionId, yearId, students.size());
                })
                .flatMapMany(Flux::fromIterable)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorMap(this::enrollmentUnavailable);
    }

    /** Evicta el roster de un combo año/grado/sección (útil tras cambios de matrícula). */
    public void evictRoster(Long gradeId, Long sectionId, Long yearId) {
        rosterCache.remove(rosterKey(gradeId, sectionId, yearId));
    }

    private String rosterKey(Long gradeId, Long sectionId, Long yearId) {
        return gradeId + "|" + sectionId + "|" + yearId;
    }

    private ServiceUnavailableException enrollmentUnavailable(Throwable error) {
        log.warn("Enrollment service unavailable: {}", error.getMessage());
        return new ServiceUnavailableException(
                "ENROLLMENT_SERVICE_UNAVAILABLE",
                "No se pudieron consultar los estudiantes matriculados. Intenta nuevamente.",
                error);
    }
}
