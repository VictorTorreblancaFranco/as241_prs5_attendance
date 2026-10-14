package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStudentSummaryResponse {
    private String estudianteId;
    private String estudianteNombre;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer total;
    private Integer present;
    private Integer absent;
    private Integer late;
    private Integer lateJustified;
    private Integer justified;
    private Double attendanceRate;
}
