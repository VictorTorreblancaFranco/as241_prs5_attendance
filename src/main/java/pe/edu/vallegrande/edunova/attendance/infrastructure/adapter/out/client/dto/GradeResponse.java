package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GradeResponse {
    private Integer id;
    private Integer academicYearId;
    private String name;
    private Boolean active;
}
