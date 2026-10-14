package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceUpdateRequest {
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
