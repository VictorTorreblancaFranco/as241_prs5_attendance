package pe.edu.vallegrande.edunova.attendance.application.port.in.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAuditResponse {
    private Long id;
    private Long attendanceId;
    private String changedBy;
    private String changedByName;
    private String changedByRole;
    private String action;
    private String previousEstado;
    private String newEstado;
    private String previousHoraLlegada;
    private String newHoraLlegada;
    private String previousJustificacionNota;
    private String newJustificacionNota;
    private String previousJustificacionFotoUrl;
    private String newJustificacionFotoUrl;
    private String motivoCambio;
    private LocalDateTime changedAt;
}
