package com.vg.attendance.infrastructure.adapter.out.persistence;

import com.vg.attendance.domain.model.Attendance;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface AttendanceR2dbcRepository extends R2dbcRepository<Attendance, Long> {
    
    Flux<Attendance> findByEstudianteIdOrderByFechaDesc(String estudianteId);
    
    Flux<Attendance> findByClaseIdAndFecha(String claseId, LocalDate fecha);
    
    Flux<Attendance> findByEstudianteIdAndFechaBetween(String estudianteId, LocalDate startDate, LocalDate endDate);
    
    Flux<Attendance> findByFecha(LocalDate fecha);

    Flux<Attendance> findTop30ByEstudianteIdOrderByFechaDesc(String estudianteId);
    
    @Query("SELECT EXISTS(SELECT 1 FROM asistencias WHERE estudiante_id = $1 AND clase_id = $2 AND fecha = $3)")
    Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String estudianteId, String claseId, LocalDate fecha);

    @Query("SELECT COUNT(*) FROM asistencias WHERE session_id = $1")
    Mono<Long> countBySessionId(Long sessionId);

    @Query("SELECT * FROM asistencias WHERE id = :id AND institucion_id = :tenantId")
    Mono<Attendance> findByIdAndTenant(Long id, UUID tenantId);

    @Query("SELECT * FROM asistencias WHERE estudiante_id = :studentId AND institucion_id = :tenantId ORDER BY fecha DESC")
    Flux<Attendance> findByStudentAndTenant(String studentId, UUID tenantId);

    @Query("SELECT * FROM asistencias WHERE clase_id = :classId AND fecha = :date AND institucion_id = :tenantId")
    Flux<Attendance> findByClassAndDateAndTenant(String classId, LocalDate date, UUID tenantId);

    @Query("SELECT * FROM asistencias WHERE estudiante_id = :studentId AND fecha BETWEEN :startDate AND :endDate AND institucion_id = :tenantId")
    Flux<Attendance> findByStudentAndRangeAndTenant(String studentId, LocalDate startDate, LocalDate endDate, UUID tenantId);

    @Query("SELECT EXISTS(SELECT 1 FROM asistencias WHERE estudiante_id = :studentId AND clase_id = :classId AND fecha = :date AND institucion_id = :tenantId)")
    Mono<Boolean> existsByStudentClassDateAndTenant(String studentId, String classId, LocalDate date, UUID tenantId);

    @Query("SELECT COUNT(*) FROM asistencias WHERE session_id = :sessionId AND institucion_id = :tenantId")
    Mono<Long> countBySessionAndTenant(Long sessionId, UUID tenantId);

    @Query("SELECT * FROM asistencias WHERE fecha = :date AND institucion_id = :tenantId")
    Flux<Attendance> findByDateAndTenant(LocalDate date, UUID tenantId);
}
