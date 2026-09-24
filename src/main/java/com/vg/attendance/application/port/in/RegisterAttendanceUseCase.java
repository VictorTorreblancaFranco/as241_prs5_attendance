package com.vg.attendance.application.port.in;

import com.vg.attendance.application.port.in.command.RegisterAttendanceCommand;
import com.vg.attendance.application.port.in.dto.AttendanceResponse;
import reactor.core.publisher.Mono;
import java.time.LocalDate;

public interface RegisterAttendanceUseCase {
    Mono<AttendanceResponse> registerAttendance(RegisterAttendanceCommand command);
    default Mono<Void> refreshSessionCounters(String claseId, LocalDate fecha, Integer totalEstudiantes, String actor) {
        return Mono.empty();
    }
}
