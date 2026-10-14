package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParentStudentLinkResponse {
    private Long id;
    private String parentId;
    private String studentId;
    private Boolean isPrimary;
}
