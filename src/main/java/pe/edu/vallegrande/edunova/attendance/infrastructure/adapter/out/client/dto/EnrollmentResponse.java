package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentResponse {
    private Long id;
    private String studentId;
    private String studentName;
    private Long academicYearId;
    private Long gradeId;
    private Long sectionId;
    private String statusId;
    private Boolean isActive;
}
