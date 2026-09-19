package com.vg.attendance.infrastructure.adapter.out.client;

import com.vg.attendance.infrastructure.adapter.out.client.dto.CourseResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.AcademicYearResponse;
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

    @Value("${attendance.clients.timeout:5s}")
    private Duration requestTimeout = Duration.ofSeconds(5);

    @Autowired
    public AcademicClient(@Qualifier("academicWebClient") WebClient academicWebClient,
                          @Qualifier("courseWebClient") WebClient courseWebClient) {
        this.academicWebClient = academicWebClient;
        this.courseWebClient = courseWebClient;
    }

    public AcademicClient(WebClient webClient) {
        this(webClient, webClient);
    }

    public Mono<CourseResponse> getCourseById(Long id) {
        return courseWebClient.get()
                .uri("/api/course/courses/{id}", id)
                .header("X-Internal-Request", "gateway")
                .retrieve()
                .bodyToMono(CourseResponse.class)
                .timeout(requestTimeout)
                .onErrorResume(error -> {
                    log.warn("Course service unavailable while resolving course {}: {}", id, error.getMessage());
                    return Mono.empty();
                });
    }

    public reactor.core.publisher.Flux<AcademicYearResponse> getAcademicYears() {
        return academicWebClient.get()
                .uri("/api/academic/years")
                .retrieve()
                .bodyToFlux(AcademicYearResponse.class)
                .timeout(requestTimeout);
    }
}
