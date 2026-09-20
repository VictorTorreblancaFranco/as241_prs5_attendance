package com.vg.attendance.infrastructure.adapter.in.web.controller;

import com.vg.attendance.application.port.in.GetAttendanceUseCase;
import com.vg.attendance.application.port.in.GetAttendanceAuditUseCase;
import com.vg.attendance.application.port.in.RegisterAttendanceUseCase;
import com.vg.attendance.application.port.in.UpdateAttendanceUseCase;
import com.vg.attendance.application.port.in.command.RegisterAttendanceCommand;
import com.vg.attendance.application.port.in.command.UpdateAttendanceCommand;
import com.vg.attendance.application.port.in.dto.AttendanceAuditResponse;
import com.vg.attendance.application.port.in.dto.AttendanceResponse;
import com.vg.attendance.domain.exception.ForbiddenException;
import com.vg.attendance.domain.exception.NotFoundException;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceUpdateRequest;
import com.vg.attendance.infrastructure.adapter.in.web.mapper.AttendanceWebMapper;
import com.vg.attendance.infrastructure.adapter.out.client.UserClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceControllerTest {

    private FakeGetAttendanceUseCase getUseCase;
    private FakeUpdateAttendanceUseCase updateUseCase;
    private AttendanceController controller;

    @BeforeEach
    void setUp() {
        getUseCase = new FakeGetAttendanceUseCase();
        updateUseCase = new FakeUpdateAttendanceUseCase();
        controller = new AttendanceController(
            command -> Mono.empty(),
            getUseCase,
            new FakeGetAttendanceAuditUseCase(),
            updateUseCase,
            new AttendanceWebMapper(),
            null,
            null,
            fakeUserClient());
    }

    @Test
    void updateAttendanceAllowsOwnerTeacher() {
        StepVerifier.create(controller.updateAttendance(
                7L,
                updateRequest(),
                "Bearer token",
                "teacher-1",
                "TEACHER"))
            .expectNextMatches(response -> "F".equals(response.getEstado()))
            .verifyComplete();

        assertThat(updateUseCase.updateCalls).isEqualTo(1);
    }

    @Test
    void updateAttendanceAllowsSecretary() {
        StepVerifier.create(controller.updateAttendance(
                7L,
                updateRequest(),
                "Bearer token",
                "secretary-1",
                "SECRETARIA"))
            .expectNextMatches(response -> "F".equals(response.getEstado()))
            .verifyComplete();

        assertThat(updateUseCase.updateCalls).isEqualTo(1);
    }

    @Test
    void updateAttendanceRejectsNonOwnerTeacher() {
        StepVerifier.create(controller.updateAttendance(
                7L,
                updateRequest(),
                "Bearer token",
                "teacher-2",
                "TEACHER"))
            .expectError(ForbiddenException.class)
            .verify();

        assertThat(updateUseCase.updateCalls).isZero();
    }

    @Test
    void updateAttendanceAllowsAdminRole() {
        StepVerifier.create(controller.updateAttendance(
                7L,
                updateRequest(),
                "Bearer token",
                "admin-1",
                "ADMIN"))
            .expectNextMatches(response -> "F".equals(response.getEstado()))
            .verifyComplete();

        assertThat(updateUseCase.updateCalls).isEqualTo(1);
    }

    @Test
    void getAttendanceByIdUsesReadableFallbackWhenUserServiceDoesNotResolveNames() {
        AttendanceController controllerWithUnavailableUsers = new AttendanceController(
            command -> Mono.empty(),
            getUseCase,
            new FakeGetAttendanceAuditUseCase(),
            updateUseCase,
            new AttendanceWebMapper(),
            null,
            null,
            emptyUserClient());

        StepVerifier.create(controllerWithUnavailableUsers.getAttendanceById(
                7L,
                "Bearer token",
                "admin-1",
                "ADMIN"))
            .assertNext(response -> {
                assertThat(response.getEstudianteNombre()).isEqualTo("Usuario no sincronizado");
                assertThat(response.getProfesorNombre()).isEqualTo("Usuario no sincronizado");
                assertThat(response.getRegistradoPorNombre()).isEqualTo("Usuario no sincronizado");
            })
            .verifyComplete();
    }

    private AttendanceUpdateRequest updateRequest() {
        return AttendanceUpdateRequest.builder()
            .estado("F")
            .motivoCambio("Corrección")
            .build();
    }

    private AttendanceResponse attendanceForAccess() {
        return AttendanceResponse.builder()
            .id(7L)
            .estudianteId("student-1")
            .claseId("class-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.now().minusDays(1))
            .anioLectivo(2026)
            .estado("A")
            .build();
    }

    private AttendanceResponse updatedAttendanceWithoutNames() {
        return AttendanceResponse.builder()
            .id(7L)
            .estudianteId("student-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.now().minusDays(1))
            .anioLectivo(2026)
            .estado("F")
            .build();
    }

    private UserClient fakeUserClient() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse
            .create(HttpStatus.OK)
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body("""
                {"id":"user-1","firstName":"Usuario","lastName":"Prueba","email":"user@test.com"}
                """)
            .build());
        return new UserClient(WebClient.builder().exchangeFunction(exchangeFunction).build(),
            CircuitBreakerRegistry.ofDefaults(),
            RetryRegistry.ofDefaults());
    }

    private UserClient emptyUserClient() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse
            .create(HttpStatus.NOT_FOUND)
            .build());
        return new UserClient(WebClient.builder().exchangeFunction(exchangeFunction).build(),
            CircuitBreakerRegistry.ofDefaults(),
            RetryRegistry.ofDefaults());
    }

    private class FakeGetAttendanceUseCase implements GetAttendanceUseCase {
        @Override
        public Mono<AttendanceResponse> getAttendanceById(Long id) {
            return id.equals(7L) ? Mono.just(attendanceForAccess()) : Mono.error(new NotFoundException("No encontrada"));
        }

        @Override
        public Flux<AttendanceResponse> getAttendanceByStudent(String estudianteId) {
            return Flux.empty();
        }

        @Override
        public Flux<AttendanceResponse> getAttendanceByClass(String claseId, LocalDate fecha) {
            return Flux.empty();
        }

        @Override
        public Flux<AttendanceResponse> getAttendanceByDate(LocalDate fecha) {
            return Flux.empty();
        }

        @Override
        public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> getDailyActivity(LocalDate since) {
            return Flux.empty();
        }

        @Override
        public Flux<com.vg.attendance.application.port.in.dto.HourlyFlowCount> getHourlyFlow(LocalDate fecha) {
            return Flux.empty();
        }

        @Override
        public Flux<AttendanceResponse> getAttendanceByDateRange(String estudianteId, LocalDate startDate, LocalDate endDate) {
            return Flux.empty();
        }
    }

    private class FakeUpdateAttendanceUseCase implements UpdateAttendanceUseCase {
        private int updateCalls;

        @Override
        public Mono<AttendanceResponse> updateAttendance(Long id, UpdateAttendanceCommand command) {
            updateCalls++;
            return Mono.just(updatedAttendanceWithoutNames());
        }

    }

    private class FakeGetAttendanceAuditUseCase implements GetAttendanceAuditUseCase {
        @Override
        public Flux<AttendanceAuditResponse> getAuditByAttendanceId(Long attendanceId) {
            return Flux.empty();
        }
    }

}
