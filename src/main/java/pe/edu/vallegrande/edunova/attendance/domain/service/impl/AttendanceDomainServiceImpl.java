package pe.edu.vallegrande.edunova.attendance.domain.service.impl;

import pe.edu.vallegrande.edunova.attendance.domain.exception.BusinessException;
import pe.edu.vallegrande.edunova.attendance.domain.model.Attendance;
import pe.edu.vallegrande.edunova.attendance.domain.service.AttendanceDomainService;
import pe.edu.vallegrande.edunova.attendance.domain.valueobject.AttendanceStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.time.LocalTime;
import java.time.Duration;

@Component
public class AttendanceDomainServiceImpl implements AttendanceDomainService {
    
    @Override
    public Mono<Attendance> validateAttendance(Attendance attendance) {
        AttendanceStatus status = AttendanceStatus.fromCode(attendance.getEstado());
        
        if (status == AttendanceStatus.TARDANZA && attendance.getHoraLlegada() == null) {
            return Mono.error(new BusinessException("La tardanza requiere hora de llegada"));
        }

        if (status != AttendanceStatus.TARDANZA && attendance.getHoraLlegada() != null) {
            return Mono.error(new BusinessException("Solo la tardanza puede registrar hora de llegada"));
        }
        
        if (status == AttendanceStatus.JUSTIFICADO &&
                (attendance.getJustificacionNota() == null || attendance.getJustificacionNota().isBlank())) {
            return Mono.error(new BusinessException("La justificación requiere una nota"));
        }

        if (status != AttendanceStatus.JUSTIFICADO && status != AttendanceStatus.TARDANZA
                && attendance.getJustificacionNota() != null
                && !attendance.getJustificacionNota().isBlank()) {
            return Mono.error(new BusinessException("Solo una falta justificada o tardanza puede tener nota de justificación"));
        }

        if (attendance.getJustificacionNota() != null && attendance.getJustificacionNota().length() > 500) {
            return Mono.error(new BusinessException("La nota de justificación no puede superar 500 caracteres"));
        }
        
        return Mono.just(attendance);
    }
    
    @Override
    public int calculateLateMinutes(LocalTime arrivalTime, LocalTime classStartTime) {
        if (arrivalTime == null || classStartTime == null) return 0;
        if (arrivalTime.isBefore(classStartTime)) return 0;
        return (int) Duration.between(classStartTime, arrivalTime).toMinutes();
    }
    
    @Override
    public String getStatusDescription(String statusCode) {
        return AttendanceStatus.fromCode(statusCode).getDescription();
    }
}
