package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.mapper;

import pe.edu.vallegrande.edunova.attendance.application.port.in.command.RegisterAttendanceCommand;
import pe.edu.vallegrande.edunova.attendance.application.port.in.command.UpdateAttendanceCommand;
import pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.dto.AttendanceRequest;
import pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.in.web.dto.AttendanceUpdateRequest;
import org.springframework.stereotype.Component;

@Component
public class AttendanceWebMapper {
    
    public RegisterAttendanceCommand toCommand(AttendanceRequest request) {
        return RegisterAttendanceCommand.builder()
            .estudianteId(request.getEstudianteId())
            .claseId(request.getClaseId())
            .profesorId(request.getProfesorId())
            .registradoPor(request.getRegistradoPor())
            .fecha(request.getFecha())
            .anioLectivo(request.getAnioLectivo())
            .estado(request.getEstado())
            .horaLlegada(request.getHoraLlegada())
            .justificacionNota(request.getJustificacionNota())
            .justificacionFotoUrl(request.getJustificacionFotoUrl())
            .build();
    }
    
    public UpdateAttendanceCommand toCommand(AttendanceUpdateRequest request) {
        return UpdateAttendanceCommand.builder()
            .estado(request.getEstado())
            .horaLlegada(request.getHoraLlegada())
            .justificacionNota(request.getJustificacionNota())
            .justificacionFotoUrl(request.getJustificacionFotoUrl())
            .motivoCambio(request.getMotivoCambio())
            .build();
    }
}
