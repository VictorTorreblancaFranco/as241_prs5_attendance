package com.vg.attendance.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("asistencia_auditoria")
public class AttendanceAudit {

    @Id
    private Long id;

    private Long attendanceId;
    private UUID institucionId;
    private String changedBy;
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
