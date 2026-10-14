package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.persistence;

import pe.edu.vallegrande.edunova.attendance.application.port.out.AttendanceRepositoryPort;
import pe.edu.vallegrande.edunova.attendance.domain.model.Attendance;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

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
    public Flux<pe.edu.vallegrande.edunova.attendance.application.port.in.dto.DailyActivityCount> countDailySince(java.time.LocalDate since) {
        return databaseClient.sql(
                "SELECT fecha, COUNT(*) AS total FROM asistencias WHERE fecha >= :since GROUP BY fecha ORDER BY fecha")
                .bind("since", since)
                .map((row, meta) -> new pe.edu.vallegrande.edunova.attendance.application.port.in.dto.DailyActivityCount(
                        row.get("fecha", LocalDate.class),
                        row.get("total", Long.class) == null ? 0L : row.get("total", Long.class)))
                .all();
    }

    @Override
    public Flux<pe.edu.vallegrande.edunova.attendance.application.port.in.dto.HourlyFlowCount> countHourlyToday(java.time.LocalDate fecha) {
        return databaseClient.sql(
                "SELECT EXTRACT(HOUR FROM hora_llegada) AS hora, COUNT(*) AS total "
                + "FROM asistencias WHERE fecha = :fecha AND hora_llegada IS NOT NULL "
                + "GROUP BY EXTRACT(HOUR FROM hora_llegada) ORDER BY hora")
                .bind("fecha", fecha)
                .map((row, meta) -> {
                    Object hora = row.get("hora");
                    long h = ((Number) hora).longValue();
                    return new pe.edu.vallegrande.edunova.attendance.application.port.in.dto.HourlyFlowCount(
                            (int) h,
                            row.get("total", Long.class) == null ? 0L : row.get("total", Long.class));
                })
                .all();
    }

    @Override
    public Flux<Attendance> findRecentAttendanceByStudent(String estudianteId) {
        return repository.findTop30ByEstudianteIdOrderByFechaDesc(estudianteId);
    }

    @Override
    public Mono<Attendance> findById(String tenantId, Long id) {
        return repository.findByIdAndTenant(id, UUID.fromString(tenantId));
    }

    @Override
    public Flux<Attendance> findByEstudianteId(String tenantId, String estudianteId) {
        return repository.findByStudentAndTenant(estudianteId, UUID.fromString(tenantId));
    }

    @Override
    public Flux<Attendance> findByClaseIdAndFecha(String tenantId, String claseId, LocalDate fecha) {
        return repository.findByClassAndDateAndTenant(claseId, fecha, UUID.fromString(tenantId));
    }

    @Override
    public Flux<Attendance> findByEstudianteIdAndFechaBetween(String tenantId, String estudianteId, LocalDate startDate, LocalDate endDate) {
        return repository.findByStudentAndRangeAndTenant(estudianteId, startDate, endDate, UUID.fromString(tenantId));
    }

    @Override
    public Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String tenantId, String estudianteId, String claseId, LocalDate fecha) {
        return repository.existsByStudentClassDateAndTenant(estudianteId, claseId, fecha, UUID.fromString(tenantId));
    }

    @Override
    public Mono<Long> countBySessionId(String tenantId, Long sessionId) {
        return repository.countBySessionAndTenant(sessionId, UUID.fromString(tenantId));
    }

    @Override
    public Flux<Attendance> findByFecha(String tenantId, LocalDate fecha) {
        return repository.findByDateAndTenant(fecha, UUID.fromString(tenantId));
    }

    @Override
    public Flux<pe.edu.vallegrande.edunova.attendance.application.port.in.dto.DailyActivityCount> countDailySince(String tenantId, LocalDate since) {
        return databaseClient.sql("SELECT fecha, COUNT(*) AS total FROM asistencias WHERE fecha >= :since AND institucion_id = :tenantId GROUP BY fecha ORDER BY fecha")
                .bind("since", since)
                .bind("tenantId", UUID.fromString(tenantId))
                .map((row, meta) -> new pe.edu.vallegrande.edunova.attendance.application.port.in.dto.DailyActivityCount(
                        row.get("fecha", LocalDate.class), row.get("total", Long.class) == null ? 0L : row.get("total", Long.class)))
                .all();
    }
}
