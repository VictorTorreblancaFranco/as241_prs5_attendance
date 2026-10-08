package com.vg.attendance.infrastructure.adapter.in.web.controller;

import com.vg.attendance.application.port.in.GetAttendanceUseCase;
import com.vg.attendance.application.port.in.GetAttendanceAuditUseCase;
import com.vg.attendance.application.port.in.RegisterAttendanceUseCase;
import com.vg.attendance.application.port.in.UpdateAttendanceUseCase;
import com.vg.attendance.application.port.in.command.RegisterAttendanceCommand;
import com.vg.attendance.application.port.in.command.UpdateAttendanceCommand;
import com.vg.attendance.application.port.in.dto.AttendanceAuditResponse;
import com.vg.attendance.application.port.in.dto.AttendanceResponse;
import com.vg.attendance.domain.exception.BusinessException;
import com.vg.attendance.domain.exception.ConflictException;
import com.vg.attendance.domain.exception.ForbiddenException;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceBulkItemRequest;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceBulkRequest;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceDailyStatsResponse;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendancePendingStudentResponse;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceRequest;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceStudentSummaryResponse;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceSummaryResponse;
import com.vg.attendance.infrastructure.adapter.in.web.dto.AttendanceUpdateRequest;
import com.vg.attendance.infrastructure.adapter.in.web.mapper.AttendanceWebMapper;
import com.vg.attendance.infrastructure.adapter.out.client.EnrollmentClient;
import com.vg.attendance.infrastructure.adapter.out.client.AcademicClient;
import com.vg.attendance.infrastructure.adapter.out.client.ScheduleClient;
import com.vg.attendance.infrastructure.adapter.out.client.dto.AcademicYearResponse;
import com.vg.attendance.infrastructure.adapter.out.client.UserClient;
import com.vg.attendance.infrastructure.adapter.out.client.dto.EnrollmentResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.ScheduleResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private static final ZoneId SCHOOL_ZONE = ZoneId.of("America/Lima");
    private static final String USER_NOT_SYNCHRONIZED = "Usuario no sincronizado";
    
    private final RegisterAttendanceUseCase registerUseCase;
    private final GetAttendanceUseCase getUseCase;
    private final GetAttendanceAuditUseCase auditUseCase;
    private final UpdateAttendanceUseCase updateUseCase;
    private final AttendanceWebMapper mapper;
    private final AcademicClient academicClient;
    private final ScheduleClient scheduleClient;
    private final EnrollmentClient enrollmentClient;
    private final UserClient userClient;
    private final TransactionalOperator transactionalOperator;

    @Autowired
    public AttendanceController(RegisterAttendanceUseCase registerUseCase,
                                GetAttendanceUseCase getUseCase,
                                GetAttendanceAuditUseCase auditUseCase,
                                UpdateAttendanceUseCase updateUseCase,
                                AttendanceWebMapper mapper,
                                AcademicClient academicClient,
                                ScheduleClient scheduleClient,
                                EnrollmentClient enrollmentClient,
                                UserClient userClient,
                                TransactionalOperator transactionalOperator) {
        this.registerUseCase = registerUseCase;
        this.getUseCase = getUseCase;
        this.auditUseCase = auditUseCase;
        this.updateUseCase = updateUseCase;
        this.mapper = mapper;
        this.academicClient = academicClient;
        this.scheduleClient = scheduleClient;
        this.enrollmentClient = enrollmentClient;
        this.userClient = userClient;
        this.transactionalOperator = transactionalOperator;
    }

    public AttendanceController(RegisterAttendanceUseCase registerUseCase,
                                GetAttendanceUseCase getUseCase,
                                GetAttendanceAuditUseCase auditUseCase,
                                UpdateAttendanceUseCase updateUseCase,
                                AttendanceWebMapper mapper,
                                ScheduleClient scheduleClient,
                                EnrollmentClient enrollmentClient,
                                UserClient userClient) {
        this(registerUseCase, getUseCase, auditUseCase, updateUseCase, mapper, null,
                scheduleClient, enrollmentClient, userClient, null);
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AttendanceResponse> registerAttendance(
            @Valid @RequestBody AttendanceRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader("X-User-Id") String userIdFromGateway,
            @RequestHeader(value = "X-User-Role", required = false) String roleFromGateway,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        log.info("Registrando asistencia - User from gateway: {}, role: {}", userIdFromGateway, roleFromGateway);
        
        if (!canManageAnyTeacher(roleFromGateway) && !userIdFromGateway.equals(request.getProfesorId())) {
            return Mono.error(new ForbiddenException("No puedes registrar asistencia para otro profesor"));
        }

        request.setRegistradoPor(userIdFromGateway);
        
        String token = extractToken(authHeader);
        return validateStudentEnrollmentForClass(request, authHeader, token)
                .then(Mono.defer(() -> {
                    RegisterAttendanceCommand command = mapper.toCommand(request);
                    command.setRegistradoPorRole(roleFromGateway);
                    command.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
                    return registerUseCase.registerAttendance(command);
                }))
                .flatMap(response -> enrichAttendance(response, token));
    }

    @PostMapping("/class/{claseId}/bulk")
    public Flux<AttendanceResponse> registerClassAttendanceBulk(
            @PathVariable String claseId,
            @Valid @RequestBody AttendanceBulkRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        validateAttendanceMutationAccess(request.getProfesorId(), userId, role);
        String token = extractToken(authHeader);
        String operationKey = normalizeIdempotencyKey(idempotencyKey);

        return findScheduleForClass(request.getProfesorId(), claseId, authHeader)
            .flatMap(schedule -> validateAcademicYear(schedule, request.getAnioLectivo(), request.getFecha()).thenReturn(schedule))
            .flatMap(schedule -> validateClassDay(schedule, request.getFecha()).thenReturn(schedule))
            .flatMapMany(schedule -> enrollmentClient.getStudentsByGradeSectionYear(
                    schedule.getGradeId(),
                    schedule.getSectionId(),
                    schedule.getAcademicYearId(),
                    token)
                .filter(student -> student.getIsActive() == null || Boolean.TRUE.equals(student.getIsActive()))
                .filter(student -> Objects.equals(student.getGradeId(), schedule.getGradeId()))
                .filter(student -> Objects.equals(student.getSectionId(), schedule.getSectionId()))
                .filter(student -> Objects.equals(student.getAcademicYearId(), schedule.getAcademicYearId()))
                .collectList()
                .flatMapMany(students -> processBulkAttendance(claseId, request, authHeader, token, userId, role, schedule, students, operationKey)));
    }
    
    @GetMapping("/teacher/{teacherId}/today")
    public Flux<ScheduleResponse> getTeacherTodayClasses(
            @PathVariable String teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        log.info("Obteniendo clases de hoy para profesor: {}", teacherId);
        validateTeacherAccess(teacherId, userId, role);
        LocalDate requestedDate = fecha != null ? fecha : LocalDate.now(SCHOOL_ZONE);
        return scheduleClient.getClassesByTeacherOnDate(teacherId, requestedDate, authHeader);
    }
    
    @GetMapping("/teacher/{teacherId}/schedule")
    public Flux<ScheduleResponse> getTeacherSchedule(
            @PathVariable String teacherId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        log.info("Obteniendo horario completo del profesor: {}", teacherId);
        validateTeacherAccess(teacherId, userId, role);
        return scheduleClient.getClassesByTeacher(teacherId, authHeader);
    }
    
    @GetMapping("/class/{gradeId}/{sectionId}/students")
    public Flux<EnrollmentResponse> getStudentsByClass(
            @PathVariable Long gradeId,
            @PathVariable Long sectionId,
            @RequestParam Long yearId,
            @RequestParam(defaultValue = "200") int limit,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        String normalizedRole = normalizeRole(role);
        if (!canManageAnyTeacher(role) && !"TEACHER".equals(normalizedRole)) {
            return Flux.error(new ForbiddenException("Solo personal autorizado o docentes pueden consultar alumnos de una sección"));
        }

        // El token es opcional: las llamadas río abajo se autentican con los
        // headers de confianza interna (X-Internal-Request/X-User-Role).
        String token = extractToken(authHeader);
        log.info("Obteniendo alumnos para grado: {}, sección: {}, año: {}", gradeId, sectionId, yearId);
        Mono<Void> teacherScope = canManageAnyTeacher(role)
                ? Mono.empty()
                : (userId == null || userId.isBlank())
                    ? Mono.error(new ForbiddenException("No se pudo identificar al profesor autenticado"))
                    : scheduleClient.getClassesByTeacher(userId, authHeader)
                    .filter(schedule -> Objects.equals(schedule.getGradeId(), gradeId)
                            && Objects.equals(schedule.getSectionId(), sectionId)
                            && Objects.equals(schedule.getAcademicYearId(), yearId))
                    .hasElements()
                    .flatMap(allowed -> allowed
                            ? Mono.empty()
                            : Mono.error(new ForbiddenException(
                                    "No puedes consultar alumnos de una sección que no tienes asignada")));

        return teacherScope.thenMany(enrollmentClient.getStudentsByGradeSectionYear(gradeId, sectionId, yearId, token))
                .filter(student -> student.getIsActive() == null || Boolean.TRUE.equals(student.getIsActive()))
                .filter(student -> Objects.equals(student.getGradeId(), gradeId))
                .filter(student -> Objects.equals(student.getSectionId(), sectionId))
                .filter(student -> Objects.equals(student.getAcademicYearId(), yearId))
                .take(validateLimit(limit))
                .collectList()
                .flatMapMany(students -> enrichStudents(students, token));
    }
    
    @GetMapping("/{id}")
    public Mono<AttendanceResponse> getAttendanceById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return attendanceById(id)
                .flatMap(attendance -> validateAttendanceReadAccess(attendance, userId, role).thenReturn(attendance))
                .flatMap(response -> enrichAttendance(response, extractToken(authHeader)));
    }

    @GetMapping("/{id}/audit")
    public Flux<AttendanceAuditResponse> getAttendanceAudit(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String token = extractToken(authHeader);
        return attendanceById(id)
            .flatMapMany(attendance -> validateAttendanceAuditAccess(attendance, userId, role)
                .thenMany(auditByAttendanceId(id)))
            .collectList()
            .flatMapMany(audits -> enrichAuditUsers(audits, token));
    }
    
    @GetMapping("/student/{estudianteId}")
    public Flux<AttendanceResponse> getAttendanceByStudent(
            @PathVariable String estudianteId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String token = extractToken(authHeader);
        return attendanceByStudent(estudianteId)
                .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                .take(validateLimit(limit))
                .collectList()
                .flatMapMany(attendances -> enrichAttendances(attendances, token));
    }
    
    @GetMapping("/class/{claseId}")
    public Flux<AttendanceResponse> getAttendanceByClass(
            @PathVariable String claseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "200") int limit,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String token = extractToken(authHeader);
        return attendanceByClass(claseId, fecha)
                .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                .take(validateLimit(limit))
                .collectList()
                .flatMapMany(attendances -> enrichAttendances(attendances, token));
    }

    @GetMapping("/class/{claseId}/summary")
    public Mono<AttendanceSummaryResponse> getAttendanceClassSummary(
            @PathVariable String claseId,
            @RequestParam String profesorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        validateTeacherAccess(profesorId, userId, role);
        String token = extractToken(authHeader);

        return findScheduleForClass(profesorId, claseId, authHeader)
            .flatMap(schedule -> validateClassDay(schedule, fecha).thenReturn(schedule))
            .flatMap(schedule -> Mono.zip(
                enrollmentClient.getStudentsByGradeSectionYear(
                        schedule.getGradeId(),
                        schedule.getSectionId(),
                        schedule.getAcademicYearId(),
                        token)
                    .filter(student -> student.getIsActive() == null || Boolean.TRUE.equals(student.getIsActive()))
                    .filter(student -> Objects.equals(student.getGradeId(), schedule.getGradeId()))
                    .filter(student -> Objects.equals(student.getSectionId(), schedule.getSectionId()))
                    .filter(student -> Objects.equals(student.getAcademicYearId(), schedule.getAcademicYearId()))
                    .collectList(),
                attendanceByClass(claseId, fecha)
                    .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                    .collectList()))
            .flatMap(tuple -> buildSummary(claseId, profesorId, fecha, tuple.getT1(), tuple.getT2(), token));
    }

    @GetMapping("/date")
    public Flux<AttendanceResponse> getAttendanceByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(defaultValue = "500") int limit,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String token = extractToken(authHeader);
        return attendanceByDate(fecha)
                .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                .take(validateLimit(limit))
                .collectList()
                .flatMapMany(attendances -> enrichAttendances(attendances, token));
    }

    /** KPI del dashboard, siempre limitado a la institución y al rol autenticado. */
    @GetMapping("/stats/today")
    public Mono<AttendanceDailyStatsResponse> getTodayStats(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        return attendanceByDate(today)
                .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                .collectList()
                .map(attendances -> {
                    int total = attendances.size();
                    int present = (int) attendances.stream().filter(a -> "A".equals(a.getEstado())).count();
                    int late = (int) attendances.stream().filter(a -> "T".equals(a.getEstado())).count();
                    int absent = (int) attendances.stream().filter(a -> "F".equals(a.getEstado())).count();
                    int justified = (int) attendances.stream().filter(a -> "J".equals(a.getEstado())).count();
                    double punctuality = (present + late) > 0
                            ? Math.round((present * 10000.0) / (present + late)) / 100.0
                            : 0.0;
                    double attendanceRate = total > 0
                            ? Math.round(((present + late) * 10000.0) / total) / 100.0
                            : 0.0;
                    return AttendanceDailyStatsResponse.builder()
                            .fecha(today)
                            .total(total)
                            .present(present)
                            .late(late)
                            .absent(absent)
                            .justified(justified)
                            .punctualityRate(punctuality)
                            .attendanceRate(attendanceRate)
                            .build();
                });
    }

    /** KPI del dashboard: registros de asistencia por día (actividad histórica). */
    @GetMapping("/stats/activity")
    public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> getActivityStats(
            @RequestParam(defaultValue = "84") int days,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        int safeDays = Math.max(7, Math.min(days, 365));
        LocalDate end = LocalDate.now(SCHOOL_ZONE);
        LocalDate start = end.minusDays(safeDays - 1L);
        return Flux.range(0, safeDays)
                .concatMap(offset -> {
                    LocalDate date = start.plusDays(offset);
                    return attendanceByDate(date)
                            .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                            .count()
                            .map(total -> new com.vg.attendance.application.port.in.dto.DailyActivityCount(date, total));
                });
    }

    /** KPI del dashboard: flujo de ingreso por hora de llegada del día. */
    @GetMapping("/stats/hourly")
    public Flux<com.vg.attendance.application.port.in.dto.HourlyFlowCount> getHourlyFlowStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        LocalDate day = fecha != null ? fecha : LocalDate.now(SCHOOL_ZONE);
        return attendanceByDate(day)
                .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                .filter(attendance -> attendance.getHoraLlegada() != null)
                .groupBy(attendance -> attendance.getHoraLlegada().getHour())
                .flatMap(group -> group.count()
                        .map(total -> new com.vg.attendance.application.port.in.dto.HourlyFlowCount(group.key(), total)))
                .sort(java.util.Comparator.comparingInt(com.vg.attendance.application.port.in.dto.HourlyFlowCount::hora));
    }

    @GetMapping("/student/{estudianteId}/summary")
    public Mono<AttendanceStudentSummaryResponse> getStudentAttendanceSummary(
            @PathVariable String estudianteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        validateDateRange(startDate, endDate);
        String token = extractToken(authHeader);
        return validateStudentSummaryAccess(estudianteId, userId, role)
            .then(attendanceByDateRange(estudianteId, startDate, endDate).collectList())
            .flatMap(attendances -> buildStudentSummary(estudianteId, startDate, endDate, attendances, token));
    }
    
    @GetMapping("/student/{estudianteId}/range")
    public Flux<AttendanceResponse> getAttendanceByDateRange(
            @PathVariable String estudianteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "200") int limit,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        validateDateRange(startDate, endDate);
        String token = extractToken(authHeader);
        return attendanceByDateRange(estudianteId, startDate, endDate)
                .filterWhen(attendance -> canReadAttendance(attendance, userId, role))
                .take(validateLimit(limit))
                .collectList()
                .flatMapMany(attendances -> enrichAttendances(attendances, token));
    }
    
    @PutMapping("/{id}")
    public Mono<AttendanceResponse> updateAttendance(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceUpdateRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        String token = extractToken(authHeader);
        return attendanceById(id)
                .doOnNext(attendance -> validateAttendanceMutationAccess(attendance, userId, role))
                .doOnNext(attendance -> validateTeacherCorrectionRange(attendance, userId, role))
                .flatMap(attendance -> validateLateArrivalForUpdate(attendance, request, authHeader).thenReturn(attendance))
                .then(Mono.defer(() -> {
                    UpdateAttendanceCommand command = mapper.toCommand(request);
                    command.setChangedBy(userId);
                    command.setChangedByRole(role);
                    return updateUseCase.updateAttendance(id, command);
                }))
                .flatMap(response -> enrichAttendance(response, token));
    }

    private Flux<AttendanceResponse> processBulkAttendance(String claseId,
                                                           AttendanceBulkRequest request,
                                                           String authHeader,
                                                           String token,
                                                           String userId,
                                                           String role,
                                                           ScheduleResponse schedule,
                                                           List<EnrollmentResponse> students,
                                                           String idempotencyKey) {
        validateBulkStudents(request.getAsistencias(), students);
        request.getAsistencias().forEach(item -> applyLateTolerance(schedule, item));
        validateBulkItems(request.getAsistencias());

        Flux<AttendanceResponse> bulkOperation = attendanceByClass(claseId, request.getFecha())
            .collectMap(AttendanceResponse::getEstudianteId)
            .flatMapMany(existing -> {
                List<AttendanceBulkItemRequest> newItems = request.getAsistencias().stream()
                    .filter(item -> !existing.containsKey(item.getEstudianteId()))
                    .toList();
                List<AttendanceBulkItemRequest> existingItems = request.getAsistencias().stream()
                    .filter(item -> existing.containsKey(item.getEstudianteId()))
                    .toList();

                Mono<Map<String, AttendanceResponse>> newRecords = registerUseCase
                    .registerAttendanceBulk(newItems.stream()
                        .map(item -> toBulkCommand(claseId, request, userId, role, item, idempotencyKey))
                        .toList())
                    .collectMap(AttendanceResponse::getEstudianteId);

                Mono<Map<String, AttendanceResponse>> updatedRecords = Flux.fromIterable(existingItems)
                    .concatMap(item -> saveBulkItem(claseId, request, userId, role,
                        existing.get(item.getEstudianteId()), item, idempotencyKey))
                    .collectMap(AttendanceResponse::getEstudianteId);

                return Mono.zip(newRecords, updatedRecords)
                    .flatMapMany(tuple -> {
                        Map<String, AttendanceResponse> saved = new HashMap<>(tuple.getT1());
                        saved.putAll(tuple.getT2());
                        List<AttendanceResponse> ordered = request.getAsistencias().stream()
                            .map(item -> saved.get(item.getEstudianteId()))
                            .filter(Objects::nonNull)
                            .toList();
                        return registerUseCase
                            .refreshSessionCounters(claseId, request.getFecha(), students.size(), userId)
                            .thenMany(enrichAttendances(ordered, token));
                    });
            });
        // Cada alumno es una unidad independiente. No envolvemos todo el lote
        // en una sola transacción: si se corta la conexión o falla un elemento,
        // los registros ya confirmados permanecen guardados y el reintento los
        // reutiliza sin duplicarlos.
        return bulkOperation;
    }

    /**
     * Validate every item before the first database write. Bulk operations are
     * sequential, so validating lazily inside saveBulkItem could leave a
     * partially persisted batch when a later student is invalid.
     */
    private void validateBulkItems(List<AttendanceBulkItemRequest> items) {
        for (AttendanceBulkItemRequest item : items) {
            String studentId = item.getEstudianteId();
            String status = item.getEstado() == null
                ? ""
                : item.getEstado().trim().toUpperCase(Locale.ROOT);

            // A/F never carry evidence. Clear stale client fields instead of
            // rejecting the whole batch when a previous J/T draft is reused.
            if ("A".equals(status) || "F".equals(status)) {
                item.setHoraLlegada(null);
                item.setJustificacionNota(null);
                item.setJustificacionFotoUrl(null);
            }

            if ("T".equals(status) && item.getHoraLlegada() == null) {
                throw new BusinessException("La tardanza requiere hora de llegada para el estudiante " + studentId);
            }
            if ("J".equals(status)
                    && (item.getJustificacionNota() == null || item.getJustificacionNota().isBlank())) {
                throw new BusinessException("La justificación requiere una nota para el estudiante " + studentId);
            }
            if (!"T".equals(status) && item.getHoraLlegada() != null) {
                throw new BusinessException("La hora de llegada solo aplica a tardanzas para el estudiante " + studentId);
            }
            if (!"T".equals(status) && !"J".equals(status)
                    && item.getJustificacionNota() != null && !item.getJustificacionNota().isBlank()) {
                throw new BusinessException("La nota de justificación no aplica al estado " + status
                    + " para el estudiante " + studentId);
            }
        }
    }

    private Mono<AttendanceResponse> saveBulkItem(String claseId,
                                                  AttendanceBulkRequest request,
                                                  String userId,
                                                  String role,
                                                  AttendanceResponse existing,
                                                  AttendanceBulkItemRequest item,
                                                  String idempotencyKey) {
        if (existing == null) {
            RegisterAttendanceCommand command = toBulkCommand(claseId, request, userId, role, item, idempotencyKey);
            return registerUseCase.registerAttendance(command)
                .onErrorResume(ConflictException.class, conflict -> findExistingBulkAttendance(claseId, request.getFecha(), item.getEstudianteId())
                    .switchIfEmpty(Mono.error(conflict))
                    .doOnNext(saved -> log.info(
                        "Bulk attendance already existed; returning stored record for student {} and class {}",
                        item.getEstudianteId(), claseId)));
        }

        if (hasSensitiveBulkChange(existing, item)
                && (item.getMotivoCambio() == null || item.getMotivoCambio().isBlank())) {
            log.info("Se omite corrección masiva sin motivo para asistencia {} del estudiante {}", existing.getId(), existing.getEstudianteId());
            return Mono.just(existing);
        }

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado(item.getEstado())
            .horaLlegada(item.getHoraLlegada())
            .justificacionNota(item.getJustificacionNota())
            .justificacionFotoUrl(item.getJustificacionFotoUrl())
            .motivoCambio(item.getMotivoCambio())
            .changedBy(userId)
            .changedByRole(role)
            .build();
        return updateUseCase.updateAttendance(existing.getId(), command);
    }

    private RegisterAttendanceCommand toBulkCommand(String claseId,
                                                     AttendanceBulkRequest request,
                                                     String userId,
                                                     String role,
                                                     AttendanceBulkItemRequest item,
                                                     String idempotencyKey) {
        return RegisterAttendanceCommand.builder()
            .estudianteId(item.getEstudianteId())
            .claseId(claseId)
            .profesorId(request.getProfesorId())
            .registradoPor(userId)
            .registradoPorRole(role)
            .fecha(request.getFecha())
            .anioLectivo(request.getAnioLectivo())
            .estado(item.getEstado())
            .horaLlegada(item.getHoraLlegada())
            .justificacionNota(item.getJustificacionNota())
            .justificacionFotoUrl(item.getJustificacionFotoUrl())
            .totalEstudiantesSesion(request.getAsistencias().size())
            .idempotencyKey(idempotencyKey)
            .skipSessionCounterUpdate(true)
            .build();
    }

    private Mono<AttendanceResponse> findExistingBulkAttendance(String claseId, LocalDate fecha, String estudianteId) {
        return attendanceByClass(claseId, fecha)
            .filter(attendance -> estudianteId.equals(attendance.getEstudianteId()))
            .next();
    }

    private void validateBulkStudents(List<AttendanceBulkItemRequest> items, List<EnrollmentResponse> students) {
        Set<String> enrolledIds = students.stream()
            .map(EnrollmentResponse::getStudentId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> requestedIds = items.stream()
            .map(AttendanceBulkItemRequest::getEstudianteId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> seen = new HashSet<>();

        for (AttendanceBulkItemRequest item : items) {
            if (!seen.add(item.getEstudianteId())) {
                throw new BusinessException("El estudiante " + item.getEstudianteId() + " está repetido en la solicitud");
            }
            if (!enrolledIds.contains(item.getEstudianteId())) {
                throw new BusinessException("El estudiante " + item.getEstudianteId() + " no está matriculado en la clase");
            }
        }

        if (!requestedIds.containsAll(enrolledIds)) {
            List<String> notIncludedInRequest = enrolledIds.stream()
                .filter(studentId -> !requestedIds.contains(studentId))
                .limit(10)
                .toList();
            log.warn(
                "Bulk attendance request has {} enrolled students not included in the UI payload: {}",
                enrolledIds.size() - requestedIds.size(), notIncludedInRequest);
        }
    }

    private boolean hasSensitiveBulkChange(AttendanceResponse current, AttendanceBulkItemRequest item) {
        String nextStatus = item.getEstado() == null ? current.getEstado() : item.getEstado().trim().toUpperCase(Locale.ROOT);
        LocalTime nextArrivalTime = "T".equals(nextStatus) ? item.getHoraLlegada() : null;
        String nextNote = allowsEvidence(nextStatus) ? cleanText(item.getJustificacionNota()) : null;
        String nextPhoto = allowsEvidence(nextStatus) ? cleanText(item.getJustificacionFotoUrl()) : null;

        return !Objects.equals(current.getEstado(), nextStatus)
            || !Objects.equals(current.getHoraLlegada(), nextArrivalTime)
            || !Objects.equals(cleanText(current.getJustificacionNota()), nextNote)
            || !Objects.equals(cleanText(current.getJustificacionFotoUrl()), nextPhoto);
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        String normalized = value.trim();
        if (normalized.length() > 120) {
            throw new BusinessException("Idempotency-Key no puede superar 120 caracteres");
        }
        return normalized;
    }

    private Mono<AttendanceSummaryResponse> buildSummary(String claseId,
                                                         String profesorId,
                                                         LocalDate fecha,
                                                         List<EnrollmentResponse> students,
                                                         List<AttendanceResponse> attendances,
                                                         String token) {
        Set<String> registeredStudentIds = attendances.stream()
            .map(AttendanceResponse::getEstudianteId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        List<EnrollmentResponse> pending = students.stream()
            .filter(student -> !registeredStudentIds.contains(student.getStudentId()))
            .toList();

        return loadUserNames(pending.stream()
                .map(EnrollmentResponse::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList(), token)
            .map(names -> AttendanceSummaryResponse.builder()
                .claseId(claseId)
                .profesorId(profesorId)
                .fecha(fecha)
                .totalStudents(students.size())
                .registered(attendances.size())
                .present(countStatus(attendances, "A"))
                .absent(countStatus(attendances, "F"))
                .late(countStatus(attendances, "T"))
                .lateJustified(countLateJustified(attendances))
                .justified(countStatus(attendances, "J"))
                .pending(pending.size())
                .pendingStudents(pending.stream()
                    .map(student -> AttendancePendingStudentResponse.builder()
                        .estudianteId(student.getStudentId())
                        .estudianteNombre(names.getOrDefault(student.getStudentId(), shortId(student.getStudentId())))
                        .build())
                    .toList())
                .build());
    }

    private int countStatus(List<AttendanceResponse> attendances, String status) {
        return (int) attendances.stream()
            .filter(attendance -> status.equals(attendance.getEstado()))
            .count();
    }

    private Mono<AttendanceStudentSummaryResponse> buildStudentSummary(String estudianteId,
                                                                       LocalDate startDate,
                                                                       LocalDate endDate,
                                                                       List<AttendanceResponse> attendances,
                                                                       String token) {
        int total = attendances.size();
        int present = countStatus(attendances, "A");
        int late = countStatus(attendances, "T");
        int absent = countStatus(attendances, "F");
        int justified = countStatus(attendances, "J");
        int lateJustified = countLateJustified(attendances);

        return loadUserNames(List.of(estudianteId), token)
            .map(names -> AttendanceStudentSummaryResponse.builder()
                .estudianteId(estudianteId)
                .estudianteNombre(names.getOrDefault(estudianteId, shortId(estudianteId)))
                .startDate(startDate)
                .endDate(endDate)
                .total(total)
                .present(present)
                .absent(absent)
                .late(late)
                .lateJustified(lateJustified)
                .justified(justified)
                .attendanceRate(calculateAttendanceRate(total, present, late))
                .build());
    }

    private double calculateAttendanceRate(int total, int present, int late) {
        if (total == 0) {
            return 0.0;
        }
        double attended = present + late;
        return Math.round((attended * 10000.0) / total) / 100.0;
    }

    private boolean allowsEvidence(String status) {
        return "J".equals(status) || "T".equals(status);
    }

    private int countLateJustified(List<AttendanceResponse> attendances) {
        return (int) attendances.stream()
            .filter(attendance -> "T".equals(attendance.getEstado()))
            .filter(attendance -> cleanText(attendance.getJustificacionNota()) != null
                || cleanText(attendance.getJustificacionFotoUrl()) != null)
            .count();
    }

    private Mono<Void> validateStudentSummaryAccess(String estudianteId, String userId, String role) {
        if (canManageAnyTeacher(role) || Objects.equals(userId, estudianteId)) {
            return Mono.empty();
        }
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ForbiddenException("No puedes consultar el resumen de este estudiante"));
        }
        return userClient.getGuardiansByStudentId(estudianteId)
            .any(link -> userId.equals(link.getParentId()))
            .flatMap(canRead -> canRead
                ? Mono.empty()
                : Mono.error(new ForbiddenException("No puedes consultar el resumen de este estudiante")));
    }

    private Flux<AttendanceAuditResponse> enrichAuditUsers(List<AttendanceAuditResponse> audits, String token) {
        if (audits.isEmpty()) {
            return Flux.empty();
        }

        return loadUserNames(
                audits.stream().map(AttendanceAuditResponse::getChangedBy).filter(Objects::nonNull).distinct().toList(),
                token)
            .flatMapMany(names -> Flux.fromIterable(audits)
                .map(audit -> {
                    audit.setChangedByName(names.get(audit.getChangedBy()));
                    return audit;
                }));
    }

    private void validateTeacherAccess(String teacherId, String userId, String role) {
        if (!canManageAnyTeacher(role) && !teacherId.equals(userId)) {
            throw new ForbiddenException("No puedes consultar clases de otro profesor");
        }
    }

    private void validateAttendanceMutationAccess(String profesorId, String userId, String role) {
        if (canManageAttendanceMutation(role)) {
            return;
        }

        if (userId == null || !userId.equals(profesorId)) {
            throw new ForbiddenException("Solo dirección, desarrollo, secretaría, coordinación o el profesor dueño de la clase pueden modificar asistencias");
        }
    }

    private void validateAttendanceMutationAccess(AttendanceResponse attendance, String userId, String role) {
        if (canManageAttendanceMutation(role)) {
            return;
        }

        if (userId == null || !userId.equals(attendance.getProfesorId())) {
            throw new ForbiddenException("Solo dirección, desarrollo, secretaría, coordinación o el profesor dueño de la clase pueden modificar asistencias");
        }
    }

    private void validateTeacherCorrectionRange(AttendanceResponse attendance, String userId, String role) {
        if (canManageAttendanceMutation(role)) {
            return;
        }
        if (!Objects.equals(userId, attendance.getProfesorId()) || attendance.getFecha() == null) {
            return;
        }

        LocalDate minCorrectionDate = LocalDate.now(SCHOOL_ZONE).minus(1, ChronoUnit.MONTHS);
        if (attendance.getFecha().isBefore(minCorrectionDate)) {
            throw new ForbiddenException("El profesor solo puede corregir asistencias de sus clases dentro del último mes");
        }
    }

    private Mono<Void> validateAttendanceReadAccess(AttendanceResponse attendance, String userId, String role) {
        return canReadAttendance(attendance, userId, role)
            .flatMap(canRead -> canRead
                ? Mono.empty()
                : Mono.error(new ForbiddenException("No puedes consultar esta asistencia")));
    }

    private Mono<Void> validateAttendanceAuditAccess(AttendanceResponse attendance, String userId, String role) {
        if (canManageAnyTeacher(role) || Objects.equals(userId, attendance.getProfesorId())) {
            return Mono.empty();
        }
        return Mono.error(new ForbiddenException("Solo roles operativos o el profesor dueño pueden consultar auditoría"));
    }

    private Mono<Boolean> canReadAttendance(AttendanceResponse attendance, String userId, String role) {
        if (canManageAnyTeacher(role)) {
            return Mono.just(true);
        }
        if (userId == null || userId.isBlank()) {
            return Mono.just(false);
        }
        boolean directAccess = userId.equals(attendance.getEstudianteId())
                || userId.equals(attendance.getProfesorId())
                || userId.equals(attendance.getRegistradoPor());
        if (directAccess) {
            return Mono.just(true);
        }

        return userClient.getGuardiansByStudentId(attendance.getEstudianteId())
            .any(link -> userId.equals(link.getParentId()));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new BusinessException("El límite debe estar entre 1 y 1000");
        }
        return limit;
    }

    private Mono<Void> validateStudentEnrollmentForClass(AttendanceRequest request, String authHeader, String token) {
        Long classId = parseClassId(request.getClaseId());

        return scheduleClient.getClassesByTeacher(request.getProfesorId(), authHeader)
                .filter(schedule -> Objects.equals(schedule.getId(), classId))
                .next()
                .switchIfEmpty(Mono.error(new BusinessException("La clase no existe o no pertenece al profesor indicado")))
                .flatMap(schedule -> validateAcademicYear(schedule, request.getAnioLectivo(), request.getFecha()).thenReturn(schedule))
                .flatMap(schedule -> validateClassDay(schedule, request.getFecha()).thenReturn(schedule))
                .flatMap(schedule -> {
                    applyLateTolerance(schedule, request);
                    return Mono.just(schedule);
                })
                .flatMap(schedule -> enrollmentClient.getStudentsByGradeSectionYear(
                                schedule.getGradeId(),
                                schedule.getSectionId(),
                                schedule.getAcademicYearId(),
                                token)
                        .filter(student -> Objects.equals(student.getStudentId(), request.getEstudianteId()))
                        .filter(student -> student.getIsActive() == null || Boolean.TRUE.equals(student.getIsActive()))
                        .filter(student -> Objects.equals(student.getGradeId(), schedule.getGradeId()))
                        .filter(student -> Objects.equals(student.getSectionId(), schedule.getSectionId()))
                        .filter(student -> Objects.equals(student.getAcademicYearId(), schedule.getAcademicYearId()))
                        .next()
                        .switchIfEmpty(Mono.error(new BusinessException(
                                "El estudiante no está matriculado en el grado y sección de la clase"))))
                .then();
    }

    private Mono<Void> validateClassDay(ScheduleResponse schedule, LocalDate attendanceDate) {
        if (schedule.getDayOfWeek() == null || attendanceDate == null) {
            return Mono.empty();
        }

        int expectedDay = schedule.getDayOfWeek().getValue();
        int actualDay = dayOfWeekValue(attendanceDate.getDayOfWeek());
        if (expectedDay != actualDay) {
            return Mono.error(new BusinessException("La clase no corresponde al día de la fecha indicada"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateAcademicYear(ScheduleResponse schedule,
                                            Integer requestedAcademicYearId,
                                            LocalDate attendanceDate) {
        if (schedule.getAcademicYearId() == null || requestedAcademicYearId == null
                || !Objects.equals(schedule.getAcademicYearId(), requestedAcademicYearId.longValue())) {
            return Mono.error(new BusinessException(
                    "El año lectivo de la asistencia no coincide con el año lectivo de la clase"));
        }
        if (academicClient == null) {
            return Mono.empty();
        }

        return academicClient.getAcademicYears()
                .filter(year -> Objects.equals(year.getId(), requestedAcademicYearId))
                .filter(year -> Boolean.TRUE.equals(year.getActive()))
                .filter(year -> attendanceDate == null || isWithinAcademicYear(year, attendanceDate))
                .next()
                .switchIfEmpty(Mono.error(new BusinessException(
                        "El año lectivo no está activo o no corresponde a la fecha indicada")))
                .then();
    }

    private boolean isWithinAcademicYear(AcademicYearResponse year, LocalDate date) {
        return year.getStartDate() != null && year.getEndDate() != null
                && !date.isBefore(year.getStartDate())
                && !date.isAfter(year.getEndDate());
    }

    private Mono<ScheduleResponse> findScheduleForClass(String profesorId, String claseId, String authHeader) {
        Long classId = parseClassId(claseId);
        return scheduleClient.getClassesByTeacher(profesorId, authHeader)
            .filter(schedule -> Objects.equals(schedule.getId(), classId))
            .next()
            .switchIfEmpty(Mono.error(new BusinessException("La clase no existe o no pertenece al profesor indicado")));
    }

    private Mono<Void> validateLateArrivalForUpdate(AttendanceResponse attendance,
                                                    AttendanceUpdateRequest request,
                                                    String authHeader) {
        String effectiveStatus = request.getEstado() == null ? attendance.getEstado() : request.getEstado();
        if (effectiveStatus == null || !"T".equals(effectiveStatus.trim().toUpperCase(Locale.ROOT))) {
            return Mono.empty();
        }

        return findScheduleForClass(attendance.getProfesorId(), attendance.getClaseId(), authHeader)
            .doOnNext(schedule -> applyLateTolerance(
                schedule,
                request,
                effectiveStatus,
                request.getHoraLlegada() == null ? attendance.getHoraLlegada() : request.getHoraLlegada()))
            .then();
    }

    private void applyLateTolerance(ScheduleResponse schedule, AttendanceRequest request) {
        if (!isLateStatus(request.getEstado())) {
            return;
        }
        AttendanceStatusDecision decision = decideLateStatus(schedule, request.getHoraLlegada());
        request.setEstado(decision.status());
        request.setHoraLlegada(decision.arrivalTime());
    }

    private void applyLateTolerance(ScheduleResponse schedule, AttendanceBulkItemRequest item) {
        if (!isLateStatus(item.getEstado())) {
            return;
        }
        AttendanceStatusDecision decision = decideLateStatus(schedule, item.getHoraLlegada());
        item.setEstado(decision.status());
        item.setHoraLlegada(decision.arrivalTime());
    }

    private void applyLateTolerance(ScheduleResponse schedule,
                                    AttendanceUpdateRequest request,
                                    String effectiveStatus,
                                    LocalTime effectiveArrivalTime) {
        if (!isLateStatus(effectiveStatus)) {
            return;
        }
        AttendanceStatusDecision decision = decideLateStatus(schedule, effectiveArrivalTime);
        request.setEstado(decision.status());
        request.setHoraLlegada(decision.arrivalTime());
    }

    private AttendanceStatusDecision decideLateStatus(ScheduleResponse schedule, LocalTime arrivalTime) {
        if (arrivalTime == null || schedule.getStartTime() == null) {
            return new AttendanceStatusDecision("T", arrivalTime);
        }
        if (arrivalTime.isBefore(schedule.getStartTime())) {
            throw new BusinessException("La hora de llegada por tardanza no puede ser anterior al inicio de la clase");
        }
        if (schedule.getEndTime() != null && !arrivalTime.isBefore(schedule.getEndTime())) {
            throw new BusinessException("La clase ya terminó; registre falta o justificado, no tardanza");
        }
        if (!arrivalTime.isAfter(schedule.getStartTime().plusMinutes(15))) {
            return new AttendanceStatusDecision("A", null);
        }
        return new AttendanceStatusDecision("T", arrivalTime);
    }

    private boolean isLateStatus(String status) {
        return status != null && "T".equals(status.trim().toUpperCase(Locale.ROOT));
    }

    private record AttendanceStatusDecision(String status, LocalTime arrivalTime) {
    }

    private int dayOfWeekValue(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue();
    }

    private Long parseClassId(String classId) {
        try {
            return Long.valueOf(classId);
        } catch (NumberFormatException error) {
            throw new BusinessException("El identificador de clase no es válido");
        }
    }

    private boolean canManageAnyTeacher(String role) {
        String normalizedRole = normalizeRole(role);
        return Set.of("ADMIN", "DEVELOPER", "DIRECTOR", "COORDINATOR", "COORDINADOR", "SECRETARY", "SECRETARIA")
                .contains(normalizedRole);
    }

    private boolean canManageAttendanceMutation(String role) {
        String normalizedRole = normalizeRole(role);
        return Set.of("ADMIN", "DEVELOPER", "DIRECTOR", "COORDINATOR", "COORDINADOR", "SECRETARY", "SECRETARIA")
                .contains(normalizedRole);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }

    private Flux<EnrollmentResponse> enrichStudents(List<EnrollmentResponse> students, String token) {
        if (students.isEmpty()) {
            return Flux.empty();
        }

        return loadUserNames(
                students.stream().map(EnrollmentResponse::getStudentId).filter(Objects::nonNull).distinct().toList(),
                token)
                .flatMapMany(names -> Flux.fromIterable(students)
                        .map(student -> {
                            student.setStudentName(names.getOrDefault(student.getStudentId(), shortId(student.getStudentId())));
                            return student;
                        }));
    }

    private Flux<AttendanceResponse> enrichAttendances(List<AttendanceResponse> attendances, String token) {
        if (attendances.isEmpty()) {
            return Flux.empty();
        }

        List<String> userIds = attendances.stream()
                .flatMap(attendance -> java.util.stream.Stream.of(
                        attendance.getEstudianteId(),
                        attendance.getProfesorId(),
                        attendance.getRegistradoPor()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return loadUserNames(userIds, token)
                .flatMapMany(names -> Flux.fromIterable(attendances)
                        .map(attendance -> applyNames(attendance, names)));
    }

    private Mono<AttendanceResponse> enrichAttendance(AttendanceResponse attendance, String token) {
        if (attendance == null) {
            return Mono.empty();
        }

        List<String> userIds = java.util.stream.Stream.of(
                        attendance.getEstudianteId(),
                        attendance.getProfesorId(),
                        attendance.getRegistradoPor())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return loadUserNames(userIds, token)
                .map(names -> applyNames(attendance, names));
    }

    private AttendanceResponse applyNames(AttendanceResponse attendance, Map<String, String> names) {
        attendance.setEstudianteNombre(names.getOrDefault(attendance.getEstudianteId(), shortId(attendance.getEstudianteId())));
        attendance.setProfesorNombre(names.getOrDefault(attendance.getProfesorId(), shortId(attendance.getProfesorId())));
        attendance.setRegistradoPorNombre(names.getOrDefault(attendance.getRegistradoPor(), shortId(attendance.getRegistradoPor())));
        return attendance;
    }

    private Mono<Map<String, String>> loadUserNames(List<String> userIds, String token) {
        if (userIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        return Flux.fromIterable(userIds)
                .flatMap(userId -> resolveUserName(userId, token)
                        .map(name -> Map.entry(userId, name)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(HashMap::new);
    }

    private Mono<String> resolveUserName(String userId, String token) {
        return userClient.getUserById(userId)
                .map(this::formatUserName)
                .filter(name -> !name.isBlank());
    }

    private String formatUserName(UserResponse user) {
        if (user == null) {
            return "";
        }
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isBlank() ? (user.getEmail() == null ? "" : user.getEmail()) : fullName;
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private String shortId(String id) {
        if (id == null || id.isBlank()) {
            return "Sin asignar";
        }
        return USER_NOT_SYNCHRONIZED;
    }

    private Mono<AttendanceResponse> attendanceById(Long id) {
        return Mono.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null ? getUseCase.getAttendanceById(id) : getUseCase.getAttendanceById(id, tenant);
        });
    }

    private Flux<AttendanceResponse> attendanceByStudent(String studentId) {
        return Flux.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null ? getUseCase.getAttendanceByStudent(studentId) : getUseCase.getAttendanceByStudent(studentId, tenant);
        });
    }

    private Flux<AttendanceResponse> attendanceByClass(String classId, LocalDate date) {
        return Flux.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null ? getUseCase.getAttendanceByClass(classId, date) : getUseCase.getAttendanceByClass(classId, date, tenant);
        });
    }

    private Flux<AttendanceResponse> attendanceByDate(LocalDate date) {
        return Flux.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null ? getUseCase.getAttendanceByDate(date) : getUseCase.getAttendanceByDate(date, tenant);
        });
    }

    private Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> dailyActivity(LocalDate since) {
        return Flux.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null ? getUseCase.getDailyActivity(since) : getUseCase.getDailyActivity(since, tenant);
        });
    }

    private Flux<AttendanceResponse> attendanceByDateRange(String studentId, LocalDate start, LocalDate end) {
        return Flux.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null
                ? getUseCase.getAttendanceByDateRange(studentId, start, end)
                : getUseCase.getAttendanceByDateRange(studentId, start, end, tenant);
        });
    }

    private Flux<AttendanceAuditResponse> auditByAttendanceId(Long attendanceId) {
        return Flux.deferContextual(context -> {
            String tenant = tenantFromContext(context);
            return tenant == null
                ? auditUseCase.getAuditByAttendanceId(attendanceId)
                : auditUseCase.getAuditByAttendanceId(attendanceId, tenant);
        });
    }

    private String tenantFromContext(reactor.util.context.ContextView context) {
        String tenant = context.getOrDefault("attendance.tenantId", null);
        return tenant == null || tenant.isBlank() ? null : tenant;
    }

}
