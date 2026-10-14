package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceBulkItemRequest {
    @NotBlank(message = "El estudiante es obligatorio")
    private String estudianteId;

    @NotBlank(message = "El estado de asistencia es obligatorio")
    @Pattern(regexp = "(?i)A|F|T|J", message = "El estado debe ser A, F, T o J")
    private String estado;

    private LocalTime horaLlegada;

    @Size(max = 500, message = "La nota de justificación no puede superar 500 caracteres")
    private String justificacionNota;

    @Size(max = 1000, message = "La URL de la evidencia de justificación no puede superar 1000 caracteres")
    private String justificacionFotoUrl;

    @Size(max = 500, message = "El motivo del cambio no puede superar 500 caracteres")
    private String motivoCambio;
}
