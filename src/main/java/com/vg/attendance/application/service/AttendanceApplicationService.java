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
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
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

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("America/Lima");
    private static final int BULK_PERSISTENCE_CONCURRENCY = 6;

    /** Días de falta seguidos que activan el aviso a la familia. */
    private static final int UMBRAL_FALTAS_CONSECUTIVAS = 3;

    /** Días hacia atrás que se revisan para contar la racha (cubre fines de semana). */
    private static final int VENTANA_RACHA_DIAS = 30;
    
    private final AttendanceRepositoryPort attendanceRepository;
    private final AttendanceAuditRepositoryPort auditRepository;
    private final AttendanceSessionRepositoryPort sessionRepository;
    private final AttendanceDomainService domainService;
    private final NotificationPort notificationPort;
    
    @Override
    public Mono<AttendanceResponse> registerAttendance(RegisterAttendanceCommand command) {
        log.info("Registering attendance for student: {} in class: {}", command.getEstudianteId(), command.getClaseId());

        return Mono.deferContextual(context -> {
            applyTenantFromContext(command, context);
            Mono<Boolean> duplicate = command.getInstitucionId() == null
                ? attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                    command.getEstudianteId(), command.getClaseId(), command.getFecha())
                : attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                    command.getInstitucionId().toString(), command.getEstudianteId(), command.getClaseId(), command.getFecha());
            return duplicate
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
                        .then(command.isSkipSessionCounterUpdate()
                            ? Mono.empty()
                            : updateSessionCounters(saved))
                        .thenReturn(saved));
            })
            .onErrorMap(this::mapDuplicateError)
            .flatMap(saved -> notifyAttendance(saved)
                .then(avisarRachaDeFaltas(saved))
                .thenReturn(saved))
            .map(this::toResponse);
        });
    }

    @Override
    public Flux<AttendanceResponse> registerAttendanceBulk(List<RegisterAttendanceCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return Flux.empty();
        }

        return Mono.deferContextual(context -> {
            commands.forEach(command -> applyTenantFromContext(command, context));
            RegisterAttendanceCommand first = commands.get(0);
            return validateAttendanceDate(first.getFecha())
                .then(ensureSession(first))
                .flatMap(session -> Flux.fromIterable(commands)
                    .flatMapSequential(command -> registerNewAttendance(command, session),
                            BULK_PERSISTENCE_CONCURRENCY)
                    .collectList()
                    .flatMap(saved -> notifyBulk(saved)
                        .then(avisarRachasDeFaltas(saved))
                        .thenReturn(saved)));
        }).flatMapMany(Flux::fromIterable)
          .map(this::toResponse);
    }

    private Mono<Attendance> registerNewAttendance(RegisterAttendanceCommand command,
                                                    AttendanceSession session) {
        Mono<Boolean> duplicate = command.getInstitucionId() == null
            ? attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                command.getEstudianteId(), command.getClaseId(), command.getFecha())
            : attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                command.getInstitucionId().toString(), command.getEstudianteId(),
                command.getClaseId(), command.getFecha());

        return duplicate.flatMap(exists -> {
            if (exists) {
                return findExistingAttendance(command)
                    .switchIfEmpty(Mono.error(new ConflictException(
                        "Ya existe un registro de asistencia para este estudiante en esta clase en esta fecha")));
            }

            return Mono.just(buildAttendance(command, session.getId()))
                .map(this::normalizeByStatus)
                .flatMap(domainService::validateAttendance)
                .flatMap(attendanceRepository::save)
                .flatMap(saved -> saveCreateAudit(saved, command).thenReturn(saved));
        }).onErrorMap(this::mapDuplicateError);
    }

    private Mono<Attendance> findExistingAttendance(RegisterAttendanceCommand command) {
        Flux<Attendance> records = command.getInstitucionId() == null
            ? attendanceRepository.findByClaseIdAndFecha(command.getClaseId(), command.getFecha())
            : attendanceRepository.findByClaseIdAndFecha(command.getInstitucionId().toString(),
                command.getClaseId(), command.getFecha());
        return records.filter(record -> Objects.equals(record.getEstudianteId(), command.getEstudianteId())).next();
    }

    private Mono<Void> notifyBulk(List<Attendance> saved) {
        return Flux.fromIterable(saved)
            .flatMap(this::notifyAttendance, BULK_PERSISTENCE_CONCURRENCY)
            .then();
    }

    @Override
    public Mono<Void> refreshSessionCounters(String claseId, LocalDate fecha,
                                              Integer totalEstudiantes, String actor) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault("attendance.tenantId", null);
            Mono<AttendanceSession> session = tenant == null
                    ? sessionRepository.findByClaseIdAndFecha(claseId, fecha)
                    : sessionRepository.findByClaseIdAndFecha(tenant, claseId, fecha);
            return session
                    .switchIfEmpty(Mono.error(new NotFoundException("Sesión de asistencia no encontrada")))
                    .flatMap(found -> refreshSession(found, tenant, totalEstudiantes, actor))
                    .then();
        });
    }
    
    @Override
    public Mono<AttendanceResponse> updateAttendance(Long id, UpdateAttendanceCommand command) {
        log.info("Updating attendance: {}", id);
        Mono<Attendance> existing = Mono.deferContextual(context -> {
            String tenant = context.getOrDefault("attendance.tenantId", null);
            return tenant == null ? attendanceRepository.findById(id) : attendanceRepository.findById(tenant, id);
        });

        return existing
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
            .flatMap(saved -> notifyAttendance(saved)
                .then(avisarRachaDeFaltas(saved))
                .thenReturn(saved))
            .map(this::toResponse);
    }
    
    @Override
    public Mono<AttendanceResponse> getAttendanceById(Long id) {
        return attendanceRepository.findById(id)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + id)))
            .map(this::toResponse);
    }

    @Override
    public Mono<AttendanceResponse> getAttendanceById(Long id, String tenantId) {
        return attendanceRepository.findById(tenantId, id)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + id)))
            .map(this::toResponse);
    }
    
    @Override
    public Flux<AttendanceResponse> getAttendanceByStudent(String estudianteId) {
        return attendanceRepository.findByEstudianteId(estudianteId)
            .map(this::toResponse);
    }

    @Override
    public Flux<AttendanceResponse> getAttendanceByStudent(String estudianteId, String tenantId) {
        return attendanceRepository.findByEstudianteId(tenantId, estudianteId).map(this::toResponse);
    }
    
    @Override
    public Flux<AttendanceResponse> getAttendanceByClass(String claseId, LocalDate fecha) {
        return attendanceRepository.findByClaseIdAndFecha(claseId, fecha)
            .map(this::toResponse);
    }

    @Override
    public Flux<AttendanceResponse> getAttendanceByClass(String claseId, LocalDate fecha, String tenantId) {
        return attendanceRepository.findByClaseIdAndFecha(tenantId, claseId, fecha).map(this::toResponse);
    }

    @Override
    public Flux<AttendanceResponse> getAttendanceByDate(LocalDate fecha) {
        return attendanceRepository.findByFecha(fecha)
            .map(this::toResponse);
    }

    @Override
    public Flux<AttendanceResponse> getAttendanceByDate(LocalDate fecha, String tenantId) {
        return attendanceRepository.findByFecha(tenantId, fecha).map(this::toResponse);
    }

    @Override
    public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> getDailyActivity(LocalDate since) {
        return attendanceRepository.countDailySince(since);
    }

    @Override
    public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> getDailyActivity(LocalDate since, String tenantId) {
        return attendanceRepository.countDailySince(tenantId, since);
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
    public Flux<AttendanceResponse> getAttendanceByDateRange(String estudianteId, LocalDate startDate, LocalDate endDate, String tenantId) {
        if (startDate.isAfter(endDate)) {
            return Flux.error(new BusinessException("La fecha de inicio no puede ser posterior a la fecha de fin"));
        }
        return attendanceRepository.findByEstudianteIdAndFechaBetween(tenantId, estudianteId, startDate, endDate)
            .map(this::toResponse);
    }

    @Override
    public Flux<AttendanceAuditResponse> getAuditByAttendanceId(Long attendanceId) {
        return attendanceRepository.findById(attendanceId)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + attendanceId)))
            .thenMany(auditRepository.findByAttendanceId(attendanceId))
            .map(this::toAuditResponse);
    }

    @Override
    public Flux<AttendanceAuditResponse> getAuditByAttendanceId(Long attendanceId, String tenantId) {
        return attendanceRepository.findById(tenantId, attendanceId)
            .switchIfEmpty(Mono.error(new NotFoundException("Asistencia no encontrada con id: " + attendanceId)))
            .thenMany(auditRepository.findByAttendanceId(tenantId, attendanceId))
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

    /**
     * Detecta la racha de faltas consecutivas y dispara el aviso de falta acumulada.
     *
     * Solo corre cuando la asistencia guardada es "F": una asistencia o una
     * justificación pueden cortar la racha, nunca crearla. Con 3 o más días de
     * ausencia se avisa UNA vez por racha, porque la clave de idempotencia en comms
     * lleva la fecha de la última falta de la racha.
     */
    private Mono<Void> avisarRachaDeFaltas(Attendance attendance) {
        if (attendance == null || !"F".equalsIgnoreCase(attendance.getEstado())
                || attendance.getEstudianteId() == null || attendance.getFecha() == null) {
            return Mono.empty();
        }
        return Mono.defer(() -> rachaDeFaltas(attendance.getEstudianteId(), attendance.getFecha()))
                .flatMap(racha -> {
                    if (racha.diasFalta() < UMBRAL_FALTAS_CONSECUTIVAS) {
                        return Mono.empty();
                    }
                    log.info("Racha de {} faltas para estudiante {} (terminó {}): se notifica a la familia",
                            racha.diasFalta(), attendance.getEstudianteId(), racha.finRacha());
                    return notificationPort.notifyThreeFullAbsenceDays(
                            attendance.getEstudianteId(), racha.diasFalta(),
                            racha.finRacha(), attendance.getProfesorId());
                })
                .onErrorResume(error -> {
                    // La ausencia ya quedó registrada: si el aviso falla, se registra
                    // el motivo y se sigue (el siguiente registro reintentará).
                    log.warn("No se pudo evaluar la racha de faltas de {}: {}",
                            attendance.getEstudianteId(), error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> avisarRachasDeFaltas(List<Attendance> guardadas) {
        if (guardadas == null || guardadas.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(guardadas)
                .flatMap(this::avisarRachaDeFaltas, BULK_PERSISTENCE_CONCURRENCY)
                .then();
    }

    /** Días de falta consecutivos hasta {@code fecha}, tolerando el fin de semana. */
    private Mono<RachaFaltas> rachaDeFaltas(String estudianteId, LocalDate fecha) {
        return attendanceRepository
                .findByEstudianteIdAndFechaBetween(estudianteId, fecha.minusDays(VENTANA_RACHA_DIAS), fecha)
                .collectList()
                .map(registros -> {
                    // Un alumno tiene varios registros por día (una por clase): para la
                    // racha importa el DÍA, no cuántas clases se perdió ese día.
                    var diasConFalta = registros.stream()
                            .filter(r -> "F".equalsIgnoreCase(r.getEstado()) && r.getFecha() != null)
                            .map(Attendance::getFecha)
                            .distinct()
                            .sorted(Comparator.reverseOrder())
                            .toList();

                    // El fin de la racha es el día MÁS RECENTE con falta (la que se
                    // acaba de registrar), no el más antiguo: es lo que identifica la
                    // racha en la clave de idempotencia del aviso.
                    LocalDate fin = diasConFalta.isEmpty() ? null : diasConFalta.get(0);
                    int dias = 0;
                    LocalDate anterior = null;
                    for (LocalDate dia : diasConFalta) {
                        // El fin de semana no corta la racha: viernes y lunes cuentan
                        // como consecutivos (de ahí el margen de 3 días).
                        if (dia.isAfter(fecha) || (anterior != null && anterior.minusDays(3).isBefore(dia))) {
                            break;
                        }
                        dias++;
                        anterior = dia;
                        if (dias >= UMBRAL_FALTAS_CONSECUTIVAS) {
                            break;
                        }
                    }
                    return new RachaFaltas(dias, fin);
                });
    }

    /** Resultado del conteo de la racha: cuántos días de falta y cuándo terminó. */
    private record RachaFaltas(int diasFalta, LocalDate finRacha) { }

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
        if (fecha.isAfter(LocalDate.now(SCHOOL_ZONE))) {
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
            .institucionId(current == null ? null : current.getInstitucionId())
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

        return auditRepository.save(audit).then();
    }

    private Mono<Void> saveCreateAudit(Attendance attendance, RegisterAttendanceCommand command) {
        AttendanceAudit audit = AttendanceAudit.builder()
            .attendanceId(attendance.getId())
            .institucionId(attendance.getInstitucionId())
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

        return auditRepository.save(audit).then();
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.toString();
    }

    private Attendance copy(Attendance attendance) {
        return Attendance.builder()
            .id(attendance.getId())
            .sessionId(attendance.getSessionId())
            .institucionId(attendance.getInstitucionId())
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
            .institucionId(command.getInstitucionId())
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
        Mono<AttendanceSession> existingByKey = command.getIdempotencyKey() == null || command.getIdempotencyKey().isBlank()
            ? Mono.empty()
            : command.getInstitucionId() == null
                ? sessionRepository.findByIdempotencyKey(command.getIdempotencyKey())
                : sessionRepository.findByIdempotencyKey(command.getInstitucionId().toString(), command.getIdempotencyKey());
        Mono<AttendanceSession> existingByClass = command.getInstitucionId() == null
            ? sessionRepository.findByClaseIdAndFecha(command.getClaseId(), command.getFecha())
            : sessionRepository.findByClaseIdAndFecha(command.getInstitucionId().toString(), command.getClaseId(), command.getFecha());
        return existingByKey.switchIfEmpty(existingByClass)
            .flatMap(session -> {
                if (!Objects.equals(session.getClaseId(), command.getClaseId())
                        || !Objects.equals(session.getFecha(), command.getFecha())
                        || !Objects.equals(session.getAnioLectivo(), command.getAnioLectivo())) {
                    return Mono.error(new ConflictException("La clave de idempotencia ya fue usada para otra sesión"));
                }
                return applySessionTotal(session, command.getTotalEstudiantesSesion());
            })
            .switchIfEmpty(Mono.defer(() -> {
                LocalDateTime now = LocalDateTime.now();
                AttendanceSession session = AttendanceSession.builder()
                    .institucionId(command.getInstitucionId())
                    .claseId(command.getClaseId())
                    .idempotencyKey(command.getIdempotencyKey())
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

        Mono<AttendanceSession> session = attendance.getInstitucionId() == null
            ? sessionRepository.findByClaseIdAndFecha(attendance.getClaseId(), attendance.getFecha())
            : sessionRepository.findByClaseIdAndFecha(attendance.getInstitucionId().toString(), attendance.getClaseId(), attendance.getFecha());
        return session.flatMap(foundSession -> refreshSession(foundSession,
                        attendance.getInstitucionId() == null ? null : attendance.getInstitucionId().toString(),
                        foundSession.getTotalEstudiantes(), attendance.getRegistradoPor()))
            .then();
    }

    private Mono<AttendanceSession> refreshSession(AttendanceSession session, String tenant,
                                                    Integer totalEstudiantes, String actor) {
        Mono<Long> count = tenant == null
                ? attendanceRepository.countBySessionId(session.getId())
                : attendanceRepository.countBySessionId(tenant, session.getId());
        return count.flatMap(recordCount -> {
            int savedRecords = Math.toIntExact(recordCount);
            if (totalEstudiantes != null && totalEstudiantes > 0) {
                session.setTotalEstudiantes(totalEstudiantes);
            }
            session.setRegistrosGuardados(savedRecords);
            if (session.getTotalEstudiantes() != null && savedRecords >= session.getTotalEstudiantes()) {
                session.setEstado("SUBMITTED");
                session.setEnviadoPor(actor);
                session.setEnviadoEn(LocalDateTime.now(SCHOOL_ZONE));
            } else {
                session.setEstado("DRAFT");
                session.setEnviadoPor(null);
                session.setEnviadoEn(null);
            }
            session.setActualizadoEn(LocalDateTime.now(SCHOOL_ZONE));
            session.setVersion(session.getVersion() == null ? 1 : session.getVersion() + 1);
            return sessionRepository.save(session);
        });
    }

    private void applyTenantFromContext(RegisterAttendanceCommand command, reactor.util.context.ContextView context) {
        if (command.getInstitucionId() == null) {
            String tenant = context.getOrDefault("attendance.tenantId", null);
            if (tenant != null && !tenant.isBlank()) {
                try {
                    command.setInstitucionId(java.util.UUID.fromString(tenant));
                } catch (IllegalArgumentException ex) {
                    throw new ForbiddenException("El tenant de la institución no es válido");
                }
            }
        }
    }

    private Throwable mapDuplicateError(Throwable error) {
        if (error instanceof DuplicateKeyException || error instanceof R2dbcDataIntegrityViolationException
                || (error.getMessage() != null && error.getMessage().toLowerCase().contains("duplicate"))) {
            return new ConflictException("Ya existe un registro de asistencia para este estudiante en esta clase en esta fecha");
        }
        return error;
    }
}
