package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client;

import pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto.EnrollmentResponse;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ServiceUnavailableException;
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
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EnrollmentClient {

    private static final String TENANT_CONTEXT_KEY = "attendance.tenantId";

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

    @Value("${attendance.roster-cache.ttl:0s}")
    private Duration rosterTtl = Duration.ZERO;

    @Value("${attendance.roster-cache.maximum-size:500}")
    private int rosterMaximumSize = 500;

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
        return Flux.deferContextual(context -> loadRoster(gradeId, sectionId, yearId, context));
    }

    private Flux<EnrollmentResponse> loadRoster(Long gradeId, Long sectionId, Long yearId, ContextView context) {
        String tenantId = context.getOrDefault(TENANT_CONTEXT_KEY, "default");
        String key = rosterKey(tenantId, gradeId, sectionId, yearId);

        RosterCacheEntry cached = rosterCache.get(key);
        long ttlMillis = Math.max(0L, rosterTtl.toMillis());
        if (ttlMillis > 0 && cached != null && System.currentTimeMillis() - cached.storedAt() < ttlMillis) {
            log.info("Roster servido desde caché para tenant {}, grado {}, sección {}, año {} ({} alumnos)",
                    tenantId, gradeId, sectionId, yearId, cached.students().size());
            return Flux.fromIterable(cached.students());
        }

        return enrollmentWebClient.get()
                .uri("/api/enrollments/grade/{gradeId}/section/{sectionId}/year/{yearId}", gradeId, sectionId, yearId)
                .retrieve()
                .bodyToFlux(EnrollmentResponse.class)
                .collectList()
                .doOnNext(students -> {
                    if (!students.isEmpty()) {
                        if (rosterCache.size() >= Math.max(1, rosterMaximumSize)) {
                            rosterCache.entrySet().stream()
                                    .min(Map.Entry.comparingByValue(java.util.Comparator.comparingLong(RosterCacheEntry::storedAt)))
                                    .map(Map.Entry::getKey)
                                    .ifPresent(rosterCache::remove);
                        }
                        rosterCache.put(key, new RosterCacheEntry(List.copyOf(uniqueStudents(students)), System.currentTimeMillis()));
                        log.info("Roster cacheado para tenant {}, grado {}, sección {}, año {}: {} alumnos",
                                tenantId, gradeId, sectionId, yearId, students.size());
                    } else {
                        // No cachear un roster vacío: una matrícula creada después
                        // debe ser visible sin esperar al TTL de la caché.
                        rosterCache.remove(key);
                        log.info("Roster vacío para tenant {}, grado {}, sección {}, año {}; no se cachea",
                                tenantId, gradeId, sectionId, yearId);
                    }
                })
                .map(this::uniqueStudents)
                .flatMapMany(Flux::fromIterable)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorMap(this::enrollmentUnavailable);
    }

    /** Evicta el roster de un combo año/grado/sección (útil tras cambios de matrícula). */
    public void evictRoster(Long gradeId, Long sectionId, Long yearId) {
        rosterCache.keySet().removeIf(key -> key.endsWith("|" + gradeId + "|" + sectionId + "|" + yearId));
    }

    private String rosterKey(String tenantId, Long gradeId, Long sectionId, Long yearId) {
        return tenantId + "|" + gradeId + "|" + sectionId + "|" + yearId;
    }

    private List<EnrollmentResponse> uniqueStudents(List<EnrollmentResponse> students) {
        Map<String, EnrollmentResponse> unique = new LinkedHashMap<>();
        students.stream()
                .filter(student -> student != null && student.getStudentId() != null)
                .forEach(student -> unique.putIfAbsent(student.getStudentId(), student));
        return List.copyOf(unique.values());
    }

    private ServiceUnavailableException enrollmentUnavailable(Throwable error) {
        log.warn("Enrollment service unavailable: {}", error.getMessage());
        return new ServiceUnavailableException(
                "ENROLLMENT_SERVICE_UNAVAILABLE",
                "No se pudieron consultar los estudiantes matriculados. Intenta nuevamente.",
                error);
    }
}
