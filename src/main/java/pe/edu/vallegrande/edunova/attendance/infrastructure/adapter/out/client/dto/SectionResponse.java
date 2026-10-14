package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectionResponse {
    private Integer id;
    private Integer gradeId;
    private String name;
    private Boolean active;
}
