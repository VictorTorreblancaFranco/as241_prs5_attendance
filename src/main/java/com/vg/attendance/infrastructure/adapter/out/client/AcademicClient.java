package com.vg.attendance.infrastructure.adapter.out.client;

import com.vg.attendance.infrastructure.adapter.out.client.dto.CourseResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.AcademicYearResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.GradeResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.SectionResponse;
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
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class AcademicClient {

    private final WebClient academicWebClient;
    private final WebClient courseWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    @Value("${attendance.clients.timeout:5s}")
    private Duration requestTimeout = Duration.ofSeconds(5);

    @Autowired
    public AcademicClient(@Qualifier("academicWebClient") WebClient academicWebClient,
                          @Qualifier("courseWebClient") WebClient courseWebClient,
                          CircuitBreakerRegistry circuitBreakerRegistry,
                          RetryRegistry retryRegistry) {
        this.academicWebClient = academicWebClient;
        this.courseWebClient = courseWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("academicService");
        this.retry = retryRegistry.retry("academicService");
    }

    public AcademicClient(WebClient webClient) {
        this(webClient, webClient, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
    }

    public Mono<CourseResponse> getCourseById(Long id) {
        return courseWebClient.get()
                .uri("/api/course/courses/{id}", id)
                .header("X-Internal-Request", "gateway")
                .retrieve()
                .bodyToMono(CourseResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorResume(error -> {
                    log.warn("Course service unavailable while resolving course {}: {}", id, error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<GradeResponse> getGradeById(Long id) {
        return academicWebClient.get()
                .uri("/api/academic/grades/{id}", id)
                .header("X-Internal-Request", "gateway")
                .retrieve()
                .bodyToMono(GradeResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorResume(error -> {
                    log.warn("Grade service unavailable while resolving grade {}: {}", id, error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<SectionResponse> getSectionById(Long id) {
        return academicWebClient.get()
                .uri("/api/academic/sections/{id}", id)
                .header("X-Internal-Request", "gateway")
                .retrieve()
                .bodyToMono(SectionResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorResume(error -> {
                    log.warn("Section service unavailable while resolving section {}: {}", id, error.getMessage());
                    return Mono.empty();
                });
    }

    public reactor.core.publisher.Flux<AcademicYearResponse> getAcademicYears() {
        return academicWebClient.get()
                .uri("/api/academic/years")
                .retrieve()
                .bodyToFlux(AcademicYearResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorMap(this::academicUnavailable);
    }

    private ServiceUnavailableException academicUnavailable(Throwable error) {
        log.warn("Academic service unavailable: {}", error.getMessage());
        return new ServiceUnavailableException(
                "ACADEMIC_SERVICE_UNAVAILABLE",
                "No se pudo consultar el año académico. Intenta nuevamente.",
                error);
    }
}
