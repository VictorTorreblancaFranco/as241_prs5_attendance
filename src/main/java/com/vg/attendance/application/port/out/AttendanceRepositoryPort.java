package com.vg.attendance.application.port.out;

import com.vg.attendance.domain.model.Attendance;
import com.vg.attendance.application.port.in.dto.DailyActivityCount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface AttendanceRepositoryPort {
    Mono<Attendance> save(Attendance attendance);
    Mono<Attendance> findById(Long id);
    Flux<Attendance> findByEstudianteId(String estudianteId);
    Flux<Attendance> findByClaseIdAndFecha(String claseId, LocalDate fecha);
    Flux<Attendance> findByEstudianteIdAndFechaBetween(String estudianteId, LocalDate startDate, LocalDate endDate);
    Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String estudianteId, String claseId, LocalDate fecha);
    Mono<Long> countBySessionId(Long sessionId);
    Flux<Attendance> findByFecha(LocalDate fecha);
    Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> countDailySince(java.time.LocalDate since);
    Flux<com.vg.attendance.application.port.in.dto.HourlyFlowCount> countHourlyToday(java.time.LocalDate fecha);
    Flux<Attendance> findRecentAttendanceByStudent(String estudianteId);

    default Mono<Attendance> findById(String tenantId, Long id) {
        return findById(id);
    }
    default Flux<Attendance> findByEstudianteId(String tenantId, String estudianteId) {
        return findByEstudianteId(estudianteId);
    }
    default Flux<Attendance> findByClaseIdAndFecha(String tenantId, String claseId, LocalDate fecha) {
        return findByClaseIdAndFecha(claseId, fecha);
    }
    default Flux<Attendance> findByEstudianteIdAndFechaBetween(String tenantId, String estudianteId, LocalDate startDate, LocalDate endDate) {
        return findByEstudianteIdAndFechaBetween(estudianteId, startDate, endDate);
    }
    default Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String tenantId, String estudianteId, String claseId, LocalDate fecha) {
        return existsByEstudianteIdAndClaseIdAndFecha(estudianteId, claseId, fecha);
    }
    default Mono<Long> countBySessionId(String tenantId, Long sessionId) {
        return countBySessionId(sessionId);
    }
    default Flux<Attendance> findByFecha(String tenantId, LocalDate fecha) {
        return findByFecha(fecha);
    }
    default Flux<DailyActivityCount> countDailySince(String tenantId, LocalDate since) {
        return countDailySince(since);
    }
}
