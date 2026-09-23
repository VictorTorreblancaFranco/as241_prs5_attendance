package com.vg.attendance.domain.model;

import com.vg.attendance.domain.valueobject.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("asistencias")
public class Attendance {
    
    @Id
    private Long id;

    private Long sessionId;

    private UUID institucionId;
    
    private String estudianteId;
    private String claseId;
    private String profesorId;
    private String registradoPor;
    
    private LocalDate fecha;
    private Integer anioLectivo;
    
    private String estado;
    private LocalTime horaLlegada;
    private String justificacionNota;
    private String justificacionFotoUrl;
    
    private LocalDateTime registradoEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    
    private Integer version;
}
