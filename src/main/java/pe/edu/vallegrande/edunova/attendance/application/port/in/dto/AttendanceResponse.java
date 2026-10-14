package pe.edu.vallegrande.edunova.attendance.application.port.in.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponse {
    private Long id;
    private Long sessionId;
    private String estudianteId;
    private String estudianteNombre;
    private String claseId;
    private String profesorId;
    private String profesorNombre;
    private String registradoPor;
    private String registradoPorNombre;
    private LocalDate fecha;
    private Integer anioLectivo;
    private String estado;
    private String estadoNombre;
    private LocalTime horaLlegada;
    private String justificacionNota;
    private String justificacionFotoUrl;
    private LocalDateTime registradoEn;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
}
