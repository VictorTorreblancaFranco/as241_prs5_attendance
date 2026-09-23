package com.vg.attendance.application.port.in;

import com.vg.attendance.application.port.in.dto.AttendanceResponse;
import com.vg.attendance.application.port.in.dto.DailyActivityCount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface GetAttendanceUseCase {
    Mono<AttendanceResponse> getAttendanceById(Long id);
    Flux<AttendanceResponse> getAttendanceByStudent(String estudianteId);
    Flux<AttendanceResponse> getAttendanceByClass(String claseId, LocalDate fecha);
    Flux<AttendanceResponse> getAttendanceByDate(LocalDate fecha);
    Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> getDailyActivity(java.time.LocalDate since);
    Flux<com.vg.attendance.application.port.in.dto.HourlyFlowCount> getHourlyFlow(java.time.LocalDate fecha);
    Flux<AttendanceResponse> getAttendanceByDateRange(String estudianteId, LocalDate startDate, LocalDate endDate);

    default Mono<AttendanceResponse> getAttendanceById(Long id, String tenantId) { return getAttendanceById(id); }
    default Flux<AttendanceResponse> getAttendanceByStudent(String estudianteId, String tenantId) { return getAttendanceByStudent(estudianteId); }
    default Flux<AttendanceResponse> getAttendanceByClass(String claseId, LocalDate fecha, String tenantId) { return getAttendanceByClass(claseId, fecha); }
    default Flux<AttendanceResponse> getAttendanceByDate(LocalDate fecha, String tenantId) { return getAttendanceByDate(fecha); }
    default Flux<DailyActivityCount> getDailyActivity(LocalDate since, String tenantId) { return getDailyActivity(since); }
    default Flux<AttendanceResponse> getAttendanceByDateRange(String estudianteId, LocalDate startDate, LocalDate endDate, String tenantId) {
        return getAttendanceByDateRange(estudianteId, startDate, endDate);
    }
}
