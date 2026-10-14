package pe.edu.vallegrande.edunova.attendance.application.port.in;

import pe.edu.vallegrande.edunova.attendance.application.port.in.dto.AttendanceAuditResponse;
import reactor.core.publisher.Flux;

public interface GetAttendanceAuditUseCase {
    Flux<AttendanceAuditResponse> getAuditByAttendanceId(Long attendanceId);

    default Flux<AttendanceAuditResponse> getAuditByAttendanceId(Long attendanceId, String tenantId) {
        return getAuditByAttendanceId(attendanceId);
    }
}
