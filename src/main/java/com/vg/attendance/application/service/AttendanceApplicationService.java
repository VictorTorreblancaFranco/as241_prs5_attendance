package com.vg.attendance.application.service;

import com.vg.attendance.application.port.in.GetAttendanceUseCase;
import com.vg.attendance.application.port.in.GetAttendanceAuditUseCase;
import com.vg.attendance.application.port.in.RegisterAttendanceUseCase;
import com.vg.attendance.application.port.in.UpdateAttendanceUseCase;
import com.vg.attendance.application.port.in.command.RegisterAttendanceCommand;
import com.vg.attendance.application.port.in.command.UpdateAttendanceCommand;
import com.vg.attendance.application.port.in.dto.AttendanceAuditResponse;
import com.vg.attendance.application.port.in.dto.AttendanceResponse;
import com.vg.attendance.application.port.out.AttendanceAuditRepositoryPort;
import com.vg.attendance.application.port.out.AttendanceRepositoryPort;
import com.vg.attendance.application.port.out.AttendanceSessionRepositoryPort;
import com.vg.attendance.application.port.out.NotificationPort;
import com.vg.attendance.domain.exception.BusinessException;
import com.vg.attendance.domain.exception.ConflictException;
import com.vg.attendance.domain.exception.ForbiddenException;
import com.vg.attendance.domain.exception.NotFoundException;
import com.vg.attendance.domain.model.Attendance;
import com.vg.attendance.domain.model.AttendanceAudit;
import com.vg.attendance.domain.model.AttendanceSession;
import com.vg.attendance.domain.service.AttendanceDomainService;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceApplicationService implements 
        RegisterAttendanceUseCase, 
        GetAttendanceUseCase, 
        GetAttendanceAuditUseCase,
        UpdateAttendanceUseCase {
    
    private final AttendanceRepositoryPort attendanceRepository;
    private final AttendanceAuditRepositoryPort auditRepository;
    private final AttendanceSessionRepositoryPort sessionRepository;
    private final AttendanceDomainService domainService;
    private final NotificationPort notificationPort;
    
    @Override
    public Mono<AttendanceResponse> registerAttendance(RegisterAttendanceCommand command) {
        log.info("Registering attendance for student: {} in class: {}", command.getEstudianteId(), command.getClaseId());
        
        return attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                command.getEstudianteId(), command.getClaseId(), command.getFecha())
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new ConflictException("Ya existe un registro de asistencia para este estudiante en esta clase en esta fecha"));
                }
                
                return validateAttendanceDate(command.getFecha())
                    .then(ensureSession(command))
                    .map(session -> buildAttendance(command, session.getId()))
                    .map(this::normalizeByStatus)
                    .flatMap(domainService::validateAttendance)
                    .flatMap(attendanceRepository::save)
                    .flatMap(saved -> saveCreateAudit(saved, command)
                        .then(updateSessionCounters(saved))
                        .thenReturn(saved));
            })
            .onErrorMap(this::mapDuplicateError)
            .flatMap(saved -> notifyAttendance(saved).thenReturn(saved))
            .map(this::toResponse);
    }
    
    @Override
    public Mono<AttendanceResponse> updateAttendance(Long id, UpdateAttendanceCommand command) {
        log.info("Updating attendance: {}", id);
        
        return attendanceRepository.findById(id)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + id)))
            .flatMap(attendance -> {
                Attendance previous = copy(attendance);
                validateEditable(attendance, command);
                if (command.getEstado() != null) {
                    attendance.setEstado(command.getEstado());
                }
                if (command.getHoraLlegada() != null) {
                    attendance.setHoraLlegada(command.getHoraLlegada());
                }
                if (command.getJustificacionNota() != null) {
                    attendance.setJustificacionNota(command.getJustificacionNota());
                }
                if (command.getJustificacionFotoUrl() != null) {
                    attendance.setJustificacionFotoUrl(command.getJustificacionFotoUrl());
                }
                attendance.setActualizadoEn(LocalDateTime.now());
                attendance.setVersion(attendance.getVersion() == null ? 1 : attendance.getVersion() + 1);
                
                Attendance normalized = normalizeByStatus(attendance);
                validateChangeReason(previous, normalized, command);
                return domainService.validateAttendance(normalized)
                    .flatMap(attendanceRepository::save)
                    .flatMap(saved -> saveAudit(previous, saved, command, "UPDATE").thenReturn(saved));
            })
            .flatMap(saved -> notifyAttendance(saved).thenReturn(saved))
            .map(this::toResponse);
    }
    
    @Override
    public Mono<AttendanceResponse> getAttendanceById(Long id) {
        return attendanceRepository.findById(id)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + id)))
            .map(this::toResponse);
    }
    
    @Override
    public Flux<AttendanceResponse> getAttendanceByStudent(String estudianteId) {
        return attendanceRepository.findByEstudianteId(estudianteId)
            .map(this::toResponse);
    }
    
    @Override
    public Flux<AttendanceResponse> getAttendanceByClass(String claseId, LocalDate fecha) {
        return attendanceRepository.findByClaseIdAndFecha(claseId, fecha)
            .map(this::toResponse);
    }

    @Override
    public Flux<AttendanceResponse> getAttendanceByDate(LocalDate fecha) {
        return attendanceRepository.findByFecha(fecha)
            .map(this::toResponse);
    }

    @Override
    public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> getDailyActivity(LocalDate since) {
        return attendanceRepository.countDailySince(since);
    }

    @Override
    public Flux<com.vg.attendance.application.port.in.dto.HourlyFlowCount> getHourlyFlow(LocalDate fecha) {
        return attendanceRepository.countHourlyToday(fecha);
    }

    @Override
    public Flux<AttendanceResponse> getAttendanceByDateRange(String estudianteId, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            return Flux.error(new BusinessException("La fecha de inicio no puede ser posterior a la fecha de fin"));
        }

        return attendanceRepository.findByEstudianteIdAndFechaBetween(estudianteId, startDate, endDate)
            .map(this::toResponse);
    }

    @Override
    public Flux<AttendanceAuditResponse> getAuditByAttendanceId(Long attendanceId) {
        return attendanceRepository.findById(attendanceId)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + attendanceId)))
            .thenMany(auditRepository.findByAttendanceId(attendanceId))
            .map(this::toAuditResponse);
    }
    
    private AttendanceResponse toResponse(Attendance attendance) {
        return AttendanceResponse.builder()
            .id(attendance.getId())
            .sessionId(attendance.getSessionId())
            .estudianteId(attendance.getEstudianteId())
            .claseId(attendance.getClaseId())
            .profesorId(attendance.getProfesorId())
            .registradoPor(attendance.getRegistradoPor())
            .fecha(attendance.getFecha())
            .anioLectivo(attendance.getAnioLectivo())
            .estado(attendance.getEstado())
            .estadoNombre(domainService.getStatusDescription(attendance.getEstado()))
            .horaLlegada(attendance.getHoraLlegada())
            .justificacionNota(attendance.getJustificacionNota())
            .justificacionFotoUrl(attendance.getJustificacionFotoUrl())
            .registradoEn(attendance.getRegistradoEn())
            .creadoEn(attendance.getCreadoEn())
            .actualizadoEn(attendance.getActualizadoEn())
            .build();
    }

    private Mono<Void> notifyAttendance(Attendance attendance) {
        return notificationPort.notifyAbsenceOrLate(attendance)
                .onErrorResume(error -> {
                    log.warn("Attendance saved but notification failed: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private AttendanceAuditResponse toAuditResponse(AttendanceAudit audit) {
        return AttendanceAuditResponse.builder()
            .id(audit.getId())
            .attendanceId(audit.getAttendanceId())
            .changedBy(audit.getChangedBy())
            .changedByRole(audit.getChangedByRole())
            .action(audit.getAction())
            .previousEstado(audit.getPreviousEstado())
            .newEstado(audit.getNewEstado())
            .previousHoraLlegada(audit.getPreviousHoraLlegada())
            .newHoraLlegada(audit.getNewHoraLlegada())
            .previousJustificacionNota(audit.getPreviousJustificacionNota())
            .newJustificacionNota(audit.getNewJustificacionNota())
            .previousJustificacionFotoUrl(audit.getPreviousJustificacionFotoUrl())
            .newJustificacionFotoUrl(audit.getNewJustificacionFotoUrl())
            .motivoCambio(audit.getMotivoCambio())
            .changedAt(audit.getChangedAt())
            .build();
    }

    private Mono<Void> validateAttendanceDate(LocalDate fecha) {
        if (fecha == null) {
            return Mono.error(new BusinessException("La fecha de asistencia es obligatoria"));
        }
        if (fecha.isAfter(LocalDate.now())) {
            return Mono.error(new BusinessException("No se puede registrar asistencia en una fecha futura"));
        }
        return Mono.empty();
    }

    private void validateEditable(Attendance attendance, UpdateAttendanceCommand command) {
        if (command == null) {
            throw new ForbiddenException("No tienes permisos para modificar esta asistencia");
        }
        if (canManageAnyAttendance(command.getChangedByRole()) || isOwnerTeacher(attendance, command.getChangedBy())) {
            return;
        }
        throw new ForbiddenException("Solo dirección, desarrollo, secretaría, coordinación o el profesor dueño de la clase pueden modificar asistencias");
    }

    private boolean canManageAnyAttendance(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return Set.of("ADMIN", "DEVELOPER", "DIRECTOR", "COORDINATOR", "COORDINADOR", "SECRETARY", "SECRETARIA")
            .contains(role.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isOwnerTeacher(Attendance attendance, String userId) {
        return attendance != null
            && userId != null
            && !userId.isBlank()
            && userId.equals(attendance.getProfesorId());
    }

    private void validateChangeReason(Attendance previous, Attendance current, UpdateAttendanceCommand command) {
        if (previous == null || current == null || !hasAuditedChange(previous, current)) {
            return;
        }
        if (command == null || command.getMotivoCambio() == null || command.getMotivoCambio().isBlank()) {
            throw new BusinessException("El motivo del cambio es obligatorio cuando se corrige la asistencia");
        }
    }

    private boolean hasAuditedChange(Attendance previous, Attendance current) {
        return !Objects.equals(previous.getEstado(), current.getEstado())
            || !Objects.equals(previous.getHoraLlegada(), current.getHoraLlegada())
            || !Objects.equals(previous.getJustificacionNota(), current.getJustificacionNota())
            || !Objects.equals(previous.getJustificacionFotoUrl(), current.getJustificacionFotoUrl());
    }

    private Attendance normalizeByStatus(Attendance attendance) {
        if (attendance == null || attendance.getEstado() == null) {
            return attendance;
        }

        attendance.setEstado(attendance.getEstado().trim().toUpperCase(Locale.ROOT));
        if (attendance.getJustificacionNota() != null) {
            attendance.setJustificacionNota(attendance.getJustificacionNota().trim());
        }
        if (attendance.getJustificacionFotoUrl() != null) {
            attendance.setJustificacionFotoUrl(attendance.getJustificacionFotoUrl().trim());
        }

        switch (attendance.getEstado()) {
            case "A", "F" -> {
                attendance.setHoraLlegada(null);
                attendance.setJustificacionNota(null);
                attendance.setJustificacionFotoUrl(null);
            }
            case "T" -> {
                attendance.setJustificacionNota(null);
                attendance.setJustificacionFotoUrl(null);
            }
            default -> {
                // J keeps the justification fields.
            }
        }

        return attendance;
    }

    private Mono<Void> saveAudit(Attendance previous, Attendance current, UpdateAttendanceCommand command, String action) {
        AttendanceAudit audit = AttendanceAudit.builder()
            .attendanceId(previous == null ? (current == null ? null : current.getId()) : previous.getId())
            .changedBy(command == null ? null : command.getChangedBy())
            .changedByRole(command == null ? null : command.getChangedByRole())
            .action(action)
            .previousEstado(previous == null ? null : previous.getEstado())
            .newEstado(current == null ? null : current.getEstado())
            .previousHoraLlegada(formatTime(previous == null ? null : previous.getHoraLlegada()))
            .newHoraLlegada(formatTime(current == null ? null : current.getHoraLlegada()))
            .previousJustificacionNota(previous == null ? null : previous.getJustificacionNota())
            .newJustificacionNota(current == null ? null : current.getJustificacionNota())
            .previousJustificacionFotoUrl(previous == null ? null : previous.getJustificacionFotoUrl())
            .newJustificacionFotoUrl(current == null ? null : current.getJustificacionFotoUrl())
            .motivoCambio(command == null ? null : command.getMotivoCambio())
            .changedAt(LocalDateTime.now())
            .build();

        return auditRepository.save(audit)
            .onErrorResume(error -> {
                log.warn("No se pudo registrar auditoria de asistencia {}: {}", audit.getAttendanceId(), error.getMessage());
                return Mono.empty();
            })
            .then();
    }

    private Mono<Void> saveCreateAudit(Attendance attendance, RegisterAttendanceCommand command) {
        AttendanceAudit audit = AttendanceAudit.builder()
            .attendanceId(attendance.getId())
            .changedBy(command.getRegistradoPor())
            .changedByRole(command.getRegistradoPorRole())
            .action("CREATE")
            .previousEstado(null)
            .newEstado(attendance.getEstado())
            .previousHoraLlegada(null)
            .newHoraLlegada(formatTime(attendance.getHoraLlegada()))
            .previousJustificacionNota(null)
            .newJustificacionNota(attendance.getJustificacionNota())
            .previousJustificacionFotoUrl(null)
            .newJustificacionFotoUrl(attendance.getJustificacionFotoUrl())
            .motivoCambio("Registro inicial de asistencia")
            .changedAt(LocalDateTime.now())
            .build();

        return auditRepository.save(audit)
            .onErrorResume(error -> {
                log.warn("No se pudo registrar auditoria inicial de asistencia {}: {}", attendance.getId(), error.getMessage());
                return Mono.empty();
            })
            .then();
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }

    private Attendance copy(Attendance attendance) {
        return Attendance.builder()
            .id(attendance.getId())
            .sessionId(attendance.getSessionId())
            .estudianteId(attendance.getEstudianteId())
            .claseId(attendance.getClaseId())
            .profesorId(attendance.getProfesorId())
            .registradoPor(attendance.getRegistradoPor())
            .fecha(attendance.getFecha())
            .anioLectivo(attendance.getAnioLectivo())
            .estado(attendance.getEstado())
            .horaLlegada(attendance.getHoraLlegada())
            .justificacionNota(attendance.getJustificacionNota())
            .justificacionFotoUrl(attendance.getJustificacionFotoUrl())
            .registradoEn(attendance.getRegistradoEn())
            .creadoEn(attendance.getCreadoEn())
            .actualizadoEn(attendance.getActualizadoEn())
            .version(attendance.getVersion())
            .build();
    }

    private Attendance buildAttendance(RegisterAttendanceCommand command, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        return Attendance.builder()
            .sessionId(sessionId)
            .estudianteId(command.getEstudianteId())
            .claseId(command.getClaseId())
            .profesorId(command.getProfesorId())
            .registradoPor(command.getRegistradoPor())
            .fecha(command.getFecha())
            .anioLectivo(command.getAnioLectivo())
            .estado(command.getEstado())
            .horaLlegada(command.getHoraLlegada())
            .justificacionNota(command.getJustificacionNota())
            .justificacionFotoUrl(command.getJustificacionFotoUrl())
            .registradoEn(now)
            .creadoEn(now)
            .actualizadoEn(now)
            .version(0)
            .build();
    }

    private Mono<AttendanceSession> ensureSession(RegisterAttendanceCommand command) {
        return sessionRepository.findByClaseIdAndFecha(command.getClaseId(), command.getFecha())
            .flatMap(session -> applySessionTotal(session, command.getTotalEstudiantesSesion()))
            .switchIfEmpty(Mono.defer(() -> {
                LocalDateTime now = LocalDateTime.now();
                AttendanceSession session = AttendanceSession.builder()
                    .claseId(command.getClaseId())
                    .profesorId(command.getProfesorId())
                    .fecha(command.getFecha())
                    .anioLectivo(command.getAnioLectivo())
                    .estado("DRAFT")
                    .totalEstudiantes(command.getTotalEstudiantesSesion())
                    .registrosGuardados(0)
                    .creadoPor(command.getRegistradoPor())
                    .creadoEn(now)
                    .actualizadoEn(now)
                    .version(0)
                    .build();
                return sessionRepository.save(session);
            }));
    }

    private Mono<AttendanceSession> applySessionTotal(AttendanceSession session, Integer totalEstudiantes) {
        if (session == null || totalEstudiantes == null || totalEstudiantes < 1
                || Objects.equals(session.getTotalEstudiantes(), totalEstudiantes)) {
            return Mono.just(session);
        }

        session.setTotalEstudiantes(totalEstudiantes);
        session.setActualizadoEn(LocalDateTime.now());
        session.setVersion(session.getVersion() == null ? 1 : session.getVersion() + 1);
        return sessionRepository.save(session);
    }

    private Mono<Void> updateSessionCounters(Attendance attendance) {
        if (attendance == null || attendance.getSessionId() == null) {
            return Mono.empty();
        }

        return sessionRepository.findByClaseIdAndFecha(attendance.getClaseId(), attendance.getFecha())
            .flatMap(session -> attendanceRepository.countBySessionId(attendance.getSessionId())
                .flatMap(count -> {
                    int savedRecords = Math.toIntExact(count);
                    session.setRegistrosGuardados(savedRecords);
                    if (session.getTotalEstudiantes() != null && savedRecords >= session.getTotalEstudiantes()) {
                        session.setEstado("SUBMITTED");
                        session.setEnviadoPor(attendance.getRegistradoPor());
                        session.setEnviadoEn(LocalDateTime.now());
                    } else {
                        session.setEstado("DRAFT");
                        session.setEnviadoPor(null);
                        session.setEnviadoEn(null);
                    }
                    session.setActualizadoEn(LocalDateTime.now());
                    session.setVersion(session.getVersion() == null ? 1 : session.getVersion() + 1);
                    return sessionRepository.save(session);
                }))
            .then();
    }

    private Throwable mapDuplicateError(Throwable error) {
        if (error instanceof DuplicateKeyException || error instanceof R2dbcDataIntegrityViolationException
                || (error.getMessage() != null && error.getMessage().toLowerCase().contains("duplicate"))) {
            return new ConflictException("Ya existe un registro de asistencia para este estudiante en esta clase en esta fecha");
        }
        return error;
    }
}
