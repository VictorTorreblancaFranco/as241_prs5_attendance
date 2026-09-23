package com.vg.attendance.infrastructure.adapter.out.persistence;

import com.vg.attendance.domain.model.AttendanceSession;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface AttendanceSessionR2dbcRepository extends R2dbcRepository<AttendanceSession, Long> {
    Mono<AttendanceSession> findByClaseIdAndFecha(String claseId, LocalDate fecha);

    @org.springframework.data.r2dbc.repository.Query("SELECT * FROM asistencia_sesiones WHERE clase_id = :classId AND fecha = :date AND institucion_id = :tenantId")
    Mono<AttendanceSession> findByClassAndDateAndTenant(String classId, LocalDate date, UUID tenantId);
}
