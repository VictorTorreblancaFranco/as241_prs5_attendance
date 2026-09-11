package com.vg.attendance.infrastructure.adapter.in.web.controller;

import com.vg.attendance.application.port.out.AttendanceRepositoryPort;
import com.vg.attendance.domain.model.Attendance;
import com.vg.attendance.infrastructure.adapter.out.client.AcademicClient;
import com.vg.attendance.infrastructure.adapter.out.client.ScheduleClient;
import com.vg.attendance.infrastructure.adapter.out.client.UserClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportControllerTest {

    @Test
    void getClassReportShouldGeneratePdfWhenExternalNamesAreUnavailable() {
        ReportController controller = new ReportController(
            new FakeAttendanceRepository(),
            unavailableUserClient(),
            unavailableScheduleClient(),
            unavailableAcademicClient());

        StepVerifier.create(controller.getClassReport(
                "class-1",
                LocalDate.of(2026, 8, 31),
                "Bearer token",
                "admin-1",
                "ADMIN"))
            .assertNext(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
                assertThat(response.getBody()).isNotNull();
                assertThat(new String(response.getBody(), 0, 4)).isEqualTo("%PDF");
            })
            .verifyComplete();
    }

    private UserClient unavailableUserClient() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse
            .create(HttpStatus.NOT_FOUND)
            .build());
        return new UserClient(WebClient.builder().exchangeFunction(exchangeFunction).build(),
            CircuitBreakerRegistry.ofDefaults(),
            RetryRegistry.ofDefaults());
    }

    private ScheduleClient unavailableScheduleClient() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse
            .create(HttpStatus.INTERNAL_SERVER_ERROR)
            .build());
        return new ScheduleClient(WebClient.builder().exchangeFunction(exchangeFunction).build(),
            CircuitBreakerRegistry.ofDefaults(),
            RetryRegistry.ofDefaults());
    }

    private AcademicClient unavailableAcademicClient() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse
            .create(HttpStatus.INTERNAL_SERVER_ERROR)
            .build());
        return new AcademicClient(WebClient.builder().exchangeFunction(exchangeFunction).build());
    }

    private static class FakeAttendanceRepository implements AttendanceRepositoryPort {

        @Override
        public Mono<Attendance> save(Attendance attendance) {
            return Mono.just(attendance);
        }

        @Override
        public Mono<Attendance> findById(Long id) {
            return Mono.empty();
        }

        @Override
        public Flux<Attendance> findByEstudianteId(String estudianteId) {
            return Flux.empty();
        }

        @Override
        public Flux<Attendance> findByClaseIdAndFecha(String claseId, LocalDate fecha) {
            return Flux.just(sampleAttendance(claseId, fecha));
        }

        @Override
        public Flux<Attendance> findByEstudianteIdAndFechaBetween(String estudianteId, LocalDate startDate, LocalDate endDate) {
            return Flux.empty();
        }

        @Override
        public Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String estudianteId, String claseId, LocalDate fecha) {
            return Mono.just(false);
        }

        @Override
        public Mono<Long> countBySessionId(Long sessionId) {
            return Mono.just(1L);
        }

        @Override
        public Flux<Attendance> findByFecha(LocalDate fecha) {
            return Flux.just(sampleAttendance("class-1", fecha));
        }

        @Override
        public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> countDailySince(LocalDate since) {
            return Flux.empty();
        }

        @Override
        public Flux<Attendance> findRecentAttendanceByStudent(String estudianteId) {
            return Flux.empty();
        }

        private Attendance sampleAttendance(String claseId, LocalDate fecha) {
            return Attendance.builder()
                .id(1L)
                .sessionId(10L)
                .estudianteId("student-1")
                .claseId(claseId)
                .profesorId("teacher-1")
                .registradoPor("teacher-1")
                .fecha(fecha)
                .anioLectivo(1)
                .estado("A")
                .horaLlegada(LocalTime.of(8, 0))
                .build();
        }
    }
}
