package pe.edu.vallegrande.edunova.attendance.application.port.out;

import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceSession;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface AttendanceSessionRepositoryPort {
    Mono<AttendanceSession> save(AttendanceSession session);
    Mono<AttendanceSession> findByClaseIdAndFecha(String claseId, LocalDate fecha);

    default Mono<AttendanceSession> findByIdempotencyKey(String idempotencyKey) {
        return Mono.empty();
    }

    default Mono<AttendanceSession> findByClaseIdAndFecha(String tenantId, String claseId, LocalDate fecha) {
        return findByClaseIdAndFecha(claseId, fecha);
    }

    default Mono<AttendanceSession> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey);
    }
}
