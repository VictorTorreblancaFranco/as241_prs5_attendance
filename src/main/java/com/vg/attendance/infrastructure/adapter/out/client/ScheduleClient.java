package com.vg.attendance.infrastructure.adapter.out.client;

import com.vg.attendance.infrastructure.adapter.out.client.dto.ScheduleResponse;
import com.vg.attendance.domain.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
public class ScheduleClient {

    private final WebClient scheduleWebClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    @Value("${attendance.clients.timeout:5s}")
    private Duration requestTimeout = Duration.ofSeconds(5);

    @Autowired
    public ScheduleClient(@Qualifier("scheduleWebClient") WebClient scheduleWebClient,
                          CircuitBreakerRegistry circuitBreakerRegistry,
                          RetryRegistry retryRegistry) {
        this.scheduleWebClient = scheduleWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("scheduleService");
        this.retry = retryRegistry.retry("scheduleService");
        log.info("ScheduleClient initialized with CircuitBreaker and Retry");
    }

    public Flux<ScheduleResponse> getTodayClassesByTeacher(String teacherId, String authHeader) {
        return getClassesByTeacherOnDate(teacherId, LocalDate.now(ZoneId.of("America/Lima")), authHeader);
    }

    public Flux<ScheduleResponse> getClassesByTeacherOnDate(String teacherId, LocalDate date, String authHeader) {
        log.info("Obteniendo clases del {} para el profesor: {}", date, teacherId);
        return scheduleWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/schedules/teacher/{teacherId}")
                        .queryParam("dayOfWeek", date.getDayOfWeek())
                        .queryParam("active", true)
                        .build(teacherId))
                .header("X-Internal-Request", "gateway")
                .retrieve()
                .bodyToFlux(ScheduleResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorMap(this::scheduleUnavailable);
    }

    public Flux<ScheduleResponse> getClassesByTeacher(String teacherId, String authHeader) {
        log.info("Obteniendo todas las clases del profesor: {}", teacherId);
        return scheduleWebClient.get()
                .uri("/schedules/teacher/{teacherId}", teacherId)
                .header("X-Internal-Request", "gateway")
                .retrieve()
                .bodyToFlux(ScheduleResponse.class)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry))
                .timeout(requestTimeout)
                .onErrorMap(this::scheduleUnavailable);
    }

    private ServiceUnavailableException scheduleUnavailable(Throwable error) {
        log.warn("Schedule service unavailable: {}", error.getMessage());
        return new ServiceUnavailableException(
                "SCHEDULE_SERVICE_UNAVAILABLE",
                "No se pudieron consultar los horarios. Intenta nuevamente.",
                error);
    }
}
