package com.vg.attendance.infrastructure.adapter.out.persistence;

import com.vg.attendance.application.port.out.AttendanceRepositoryPort;
import com.vg.attendance.domain.model.Attendance;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class AttendanceRepositoryAdapter implements AttendanceRepositoryPort {
    
    private final AttendanceR2dbcRepository repository;
    private final DatabaseClient databaseClient;
    
    @Override
    public Mono<Attendance> save(Attendance attendance) {
        return repository.save(attendance);
    }
    
    @Override
    public Mono<Attendance> findById(Long id) {
        return repository.findById(id);
    }
    
    @Override
    public Flux<Attendance> findByEstudianteId(String estudianteId) {
        return repository.findByEstudianteIdOrderByFechaDesc(estudianteId);
    }
    
    @Override
    public Flux<Attendance> findByClaseIdAndFecha(String claseId, LocalDate fecha) {
        return repository.findByClaseIdAndFecha(claseId, fecha);
    }
    
    @Override
    public Flux<Attendance> findByEstudianteIdAndFechaBetween(String estudianteId, LocalDate startDate, LocalDate endDate) {
        return repository.findByEstudianteIdAndFechaBetween(estudianteId, startDate, endDate);
    }
    
    @Override
    public Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String estudianteId, String claseId, LocalDate fecha) {
        return repository.existsByEstudianteIdAndClaseIdAndFecha(estudianteId, claseId, fecha);
    }

    @Override
    public Mono<Long> countBySessionId(Long sessionId) {
        return repository.countBySessionId(sessionId);
    }
    
    @Override
    public Flux<Attendance> findByFecha(LocalDate fecha) {
        return repository.findByFecha(fecha);
    }

    @Override
    public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> countDailySince(java.time.LocalDate since) {
        return databaseClient.sql(
                "SELECT fecha, COUNT(*) AS total FROM asistencias WHERE fecha >= :since GROUP BY fecha ORDER BY fecha")
                .bind("since", since)
                .map((row, meta) -> new com.vg.attendance.application.port.in.dto.DailyActivityCount(
                        row.get("fecha", LocalDate.class),
                        row.get("total", Long.class) == null ? 0L : row.get("total", Long.class)))
                .all();
    }

    @Override
    public Flux<Attendance> findRecentAttendanceByStudent(String estudianteId) {
        return repository.findTop30ByEstudianteIdOrderByFechaDesc(estudianteId);
    }
}
