package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.persistence;

import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceAudit;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface AttendanceAuditR2dbcRepository extends R2dbcRepository<AttendanceAudit, Long> {
    Flux<AttendanceAudit> findByAttendanceIdOrderByChangedAtDesc(Long attendanceId);
    Flux<AttendanceAudit> findByAttendanceIdAndInstitucionIdOrderByChangedAtDesc(Long attendanceId, UUID institucionId);
}
