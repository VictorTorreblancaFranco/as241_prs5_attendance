package pe.edu.vallegrande.edunova.attendance.application.port.in;

import pe.edu.vallegrande.edunova.attendance.application.port.in.command.UpdateAttendanceCommand;
import pe.edu.vallegrande.edunova.attendance.application.port.in.dto.AttendanceResponse;
import reactor.core.publisher.Mono;

public interface UpdateAttendanceUseCase {
    Mono<AttendanceResponse> updateAttendance(Long id, UpdateAttendanceCommand command);
}
