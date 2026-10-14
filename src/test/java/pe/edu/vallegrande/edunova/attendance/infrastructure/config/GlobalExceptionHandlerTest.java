package pe.edu.vallegrande.edunova.attendance.infrastructure.config;

import pe.edu.vallegrande.edunova.attendance.domain.exception.BusinessException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ConflictException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ForbiddenException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.NotFoundException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionUsesStableErrorContract() {
        StepVerifier.create(handler.handleBadRequestException(new BusinessException("La tardanza requiere hora de llegada")))
            .assertNext(response -> {
                assertThat(response).containsEntry("code", "BUSINESS_RULE_VIOLATION");
                assertThat(response).containsEntry("message", "La tardanza requiere hora de llegada");
                assertThat(response).containsEntry("status", 400);
                assertThat(response).containsKeys("details", "timestamp", "error");
            })
            .verifyComplete();
    }

    @Test
    void knownExceptionsExposeFrontendFriendlyCodes() {
        StepVerifier.create(handler.handleNotFoundException(new NotFoundException("Asistencia no encontrada")))
            .assertNext(response -> assertThat(response).containsEntry("code", "NOT_FOUND"))
            .verifyComplete();

        StepVerifier.create(handler.handleConflictException(new ConflictException("Ya existe asistencia")))
            .assertNext(response -> assertThat(response).containsEntry("code", "ATTENDANCE_ALREADY_EXISTS"))
            .verifyComplete();

        StepVerifier.create(handler.handleForbiddenException(new ForbiddenException("No tienes permisos")))
            .assertNext(response -> assertThat(response).containsEntry("code", "FORBIDDEN"))
            .verifyComplete();
    }

    @Test
    void serviceUnavailableUsesStableDependencyErrorContract() {
        StepVerifier.create(handler.handleServiceUnavailableException(new ServiceUnavailableException(
                "SCHEDULE_SERVICE_UNAVAILABLE",
                "No se pudieron consultar los horarios. Intenta nuevamente.",
                new RuntimeException("connection refused"))))
            .assertNext(response -> {
                assertThat(response).containsEntry("code", "SCHEDULE_SERVICE_UNAVAILABLE");
                assertThat(response).containsEntry("message", "No se pudieron consultar los horarios. Intenta nuevamente.");
                assertThat(response).containsEntry("status", 503);
            })
            .verifyComplete();
    }

    @Test
    void unexpectedRuntimeExceptionDoesNotExposeInternalDetails() {
        StepVerifier.create(handler.handleRuntimeException(new RuntimeException("Could not open R2DBC Connection")))
            .assertNext(response -> {
                assertThat(response).containsEntry("code", "INTERNAL_ERROR");
                assertThat(response).containsEntry("message", "Ocurrió un error inesperado. Intenta nuevamente.");
                assertThat(response).containsEntry("status", 500);
            })
            .verifyComplete();
    }
}
