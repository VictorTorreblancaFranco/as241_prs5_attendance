package pe.edu.vallegrande.edunova.attendance.application.port.out;

import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceAudit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AttendanceAuditRepositoryPort {
    Mono<AttendanceAudit> save(AttendanceAudit audit);
    Flux<AttendanceAudit> findByAttendanceId(Long attendanceId);

    default Flux<AttendanceAudit> findByAttendanceId(String tenantId, Long attendanceId) {
        return findByAttendanceId(attendanceId);
    }
}
