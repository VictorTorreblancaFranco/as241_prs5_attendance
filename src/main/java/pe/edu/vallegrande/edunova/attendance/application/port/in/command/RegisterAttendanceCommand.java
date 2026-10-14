package pe.edu.vallegrande.edunova.attendance.application.port.in.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterAttendanceCommand {
    private String estudianteId;
    private String claseId;
    private String idempotencyKey;
    private String profesorId;
    private String registradoPor;
    private String registradoPorRole;
    private LocalDate fecha;
    private Integer anioLectivo;
    private Integer totalEstudiantesSesion;
    private String estado;
    private LocalTime horaLlegada;
    private String justificacionNota;
    private String justificacionFotoUrl;
    private UUID institucionId;
    /** The bulk controller already loaded the class roster before splitting new/existing rows. */
    private boolean skipDuplicateCheck;
    private boolean skipSessionCounterUpdate;
    private boolean skipNotifications;
}
