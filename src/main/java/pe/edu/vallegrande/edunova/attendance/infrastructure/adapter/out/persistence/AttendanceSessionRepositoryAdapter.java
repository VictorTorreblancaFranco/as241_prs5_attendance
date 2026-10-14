package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.persistence;

import pe.edu.vallegrande.edunova.attendance.application.port.out.AttendanceSessionRepositoryPort;
import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AttendanceSessionRepositoryAdapter implements AttendanceSessionRepositoryPort {

    private final AttendanceSessionR2dbcRepository repository;

    @Override
    public Mono<AttendanceSession> save(AttendanceSession session) {
        return repository.save(session);
    }

    @Override
    public Mono<AttendanceSession> findByClaseIdAndFecha(String claseId, LocalDate fecha) {
        return repository.findByClaseIdAndFecha(claseId, fecha);
    }

    @Override
    public Mono<AttendanceSession> findByClaseIdAndFecha(String tenantId, String claseId, LocalDate fecha) {
        return repository.findByClassAndDateAndTenant(claseId, fecha, UUID.fromString(tenantId));
    }

    @Override
    public Mono<AttendanceSession> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Mono<AttendanceSession> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return repository.findByIdempotencyKeyAndTenant(idempotencyKey, UUID.fromString(tenantId));
    }
}
