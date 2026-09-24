package com.vg.attendance.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRequest {
    
    @NotBlank(message = "El estudiante es obligatorio")
    private String estudianteId;
    
    @NotBlank(message = "La clase es obligatoria")
    private String claseId;
    
    @NotBlank(message = "El profesor es obligatorio")
    private String profesorId;
    
    @NotBlank(message = "El usuario que registra la asistencia es obligatorio")
    private String registradoPor;
    
    @NotNull(message = "La fecha de asistencia es obligatoria")
    private LocalDate fecha;
    
    @NotNull(message = "El año lectivo es obligatorio")
    @Min(value = 2000, message = "El año lectivo no es válido")
    @Max(value = 2200, message = "El año lectivo no es válido")
    private Integer anioLectivo;
    
    @NotBlank(message = "El estado de asistencia es obligatorio")
    @Pattern(regexp = "(?i)A|F|T|J", message = "El estado debe ser A, F, T o J")
    private String estado;
    
    private LocalTime horaLlegada;
    
    @Size(max = 500, message = "La nota de justificación no puede superar 500 caracteres")
    private String justificacionNota;
    
    @Size(max = 1000, message = "La URL de la evidencia de justificación no puede superar 1000 caracteres")
    private String justificacionFotoUrl;
}
