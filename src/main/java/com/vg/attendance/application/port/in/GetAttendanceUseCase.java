package com.vg.attendance.application.port.in;

import com.vg.attendance.application.port.in.dto.AttendanceResponse;
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
}
