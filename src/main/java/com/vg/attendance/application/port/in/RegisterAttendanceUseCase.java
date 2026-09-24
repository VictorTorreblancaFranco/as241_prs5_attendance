package com.vg.attendance.application.port.in;

import com.vg.attendance.application.port.in.command.RegisterAttendanceCommand;
import com.vg.attendance.application.port.in.dto.AttendanceResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.time.LocalDate;
import java.util.List;

public interface RegisterAttendanceUseCase {
    Mono<AttendanceResponse> registerAttendance(RegisterAttendanceCommand command);

    /**
     * Registers a group of new records while reusing one attendance session.
     * Implementations may optimize the persistence path; the default keeps
     * backwards compatibility for lightweight test doubles.
     */
    default Flux<AttendanceResponse> registerAttendanceBulk(List<RegisterAttendanceCommand> commands) {
        return Flux.fromIterable(commands).concatMap(this::registerAttendance);
    }

    default Mono<Void> refreshSessionCounters(String claseId, LocalDate fecha, Integer totalEstudiantes, String actor) {
        return Mono.empty();
    }
}
