package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.persistence;

import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceSession;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface AttendanceSessionR2dbcRepository extends R2dbcRepository<AttendanceSession, Long> {
    Mono<AttendanceSession> findByClaseIdAndFecha(String claseId, LocalDate fecha);

    @org.springframework.data.r2dbc.repository.Query("SELECT * FROM asistencia_sesiones WHERE idempotency_key = :key ORDER BY id DESC LIMIT 1")
    Mono<AttendanceSession> findByIdempotencyKey(String key);

    @org.springframework.data.r2dbc.repository.Query("SELECT * FROM asistencia_sesiones WHERE clase_id = :classId AND fecha = :date AND institucion_id = :tenantId")
    Mono<AttendanceSession> findByClassAndDateAndTenant(String classId, LocalDate date, UUID tenantId);

    @org.springframework.data.r2dbc.repository.Query("SELECT * FROM asistencia_sesiones WHERE idempotency_key = :key AND institucion_id = :tenantId ORDER BY id DESC LIMIT 1")
    Mono<AttendanceSession> findByIdempotencyKeyAndTenant(String key, UUID tenantId);
}
