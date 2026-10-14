package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.persistence;

import pe.edu.vallegrande.edunova.attendance.application.port.out.AttendanceAuditRepositoryPort;
import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AttendanceAuditRepositoryAdapter implements AttendanceAuditRepositoryPort {

    private final AttendanceAuditR2dbcRepository repository;

    @Override
    public Mono<AttendanceAudit> save(AttendanceAudit audit) {
        return repository.save(audit);
    }

    @Override
    public Flux<AttendanceAudit> findByAttendanceId(Long attendanceId) {
        return repository.findByAttendanceIdOrderByChangedAtDesc(attendanceId);
    }

    @Override
    public Flux<AttendanceAudit> findByAttendanceId(String tenantId, Long attendanceId) {
        return repository.findByAttendanceIdAndInstitucionIdOrderByChangedAtDesc(
                attendanceId, UUID.fromString(tenantId));
    }
}
