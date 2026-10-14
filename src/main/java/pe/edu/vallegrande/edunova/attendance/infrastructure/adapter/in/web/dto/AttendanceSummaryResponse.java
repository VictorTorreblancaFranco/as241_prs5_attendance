package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryResponse {
    private String claseId;
    private String profesorId;
    private LocalDate fecha;
    private int totalStudents;
    private int registered;
    private int present;
    private int absent;
    private int late;
    private int lateJustified;
    private int justified;
    private int pending;
    private List<AttendancePendingStudentResponse> pendingStudents;
}
