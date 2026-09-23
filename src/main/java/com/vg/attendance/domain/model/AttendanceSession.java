package com.vg.attendance.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("asistencia_sesiones")
public class AttendanceSession {

    @Id
    private Long id;

    private UUID institucionId;

    private String claseId;
    private String profesorId;
    private LocalDate fecha;
    private Integer anioLectivo;
    private String estado;
    private Integer totalEstudiantes;
    private Integer registrosGuardados;
    private String creadoPor;
    private String enviadoPor;
    private LocalDateTime enviadoEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private Integer version;
}
