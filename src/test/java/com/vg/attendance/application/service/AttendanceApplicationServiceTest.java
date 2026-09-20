package com.vg.attendance.application.service;

import com.vg.attendance.application.port.in.command.RegisterAttendanceCommand;
import com.vg.attendance.application.port.in.command.UpdateAttendanceCommand;
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
import com.vg.attendance.domain.service.impl.AttendanceDomainServiceImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceApplicationServiceTest {

    private final InMemoryAttendanceRepository repository = new InMemoryAttendanceRepository();
    private final InMemoryAttendanceAuditRepository auditRepository = new InMemoryAttendanceAuditRepository();
    private final InMemoryAttendanceSessionRepository sessionRepository = new InMemoryAttendanceSessionRepository();
    private final AttendanceApplicationService service =
        new AttendanceApplicationService(repository, auditRepository, sessionRepository, new AttendanceDomainServiceImpl(), new NoopNotificationPort());

    @Test
    void registerAttendanceCreatesRecordWhenItDoesNotExist() {
        RegisterAttendanceCommand command = RegisterAttendanceCommand.builder()
            .estudianteId("student-1")
            .claseId("class-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.of(2026, 6, 5))
            .anioLectivo(2026)
            .estado("A")
            .build();

        StepVerifier.create(service.registerAttendance(command))
            .assertNext(response -> {
                assertThat(response.getId()).isEqualTo(1L);
                assertThat(response.getSessionId()).isEqualTo(1L);
                assertThat(response.getEstado()).isEqualTo("A");
                assertThat(response.getEstadoNombre()).isEqualTo("Asistió");
                assertThat(response.getCreadoEn()).isNotNull();
            })
            .verifyComplete();

        assertThat(sessionRepository.records).hasSize(1);
        assertThat(sessionRepository.records.get(1L).getClaseId()).isEqualTo("class-1");
    }

    @Test
    void registerAttendanceRejectsDuplicateStudentClassDate() {
        repository.save(existingAttendance(1L)).block();

        RegisterAttendanceCommand command = RegisterAttendanceCommand.builder()
            .estudianteId("student-1")
            .claseId("class-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.of(2026, 6, 5))
            .anioLectivo(2026)
            .estado("A")
            .build();

        StepVerifier.create(service.registerAttendance(command))
            .expectError(ConflictException.class)
            .verify();
    }

    @Test
    void registerAttendanceNormalizesLowercaseStatus() {
        RegisterAttendanceCommand command = RegisterAttendanceCommand.builder()
            .estudianteId("student-2")
            .claseId("class-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.of(2026, 6, 5))
            .anioLectivo(2026)
            .estado(" t ")
            .horaLlegada(LocalTime.of(8, 12))
            .justificacionNota("no debe guardarse")
            .build();

        StepVerifier.create(service.registerAttendance(command))
            .assertNext(response -> {
                assertThat(response.getEstado()).isEqualTo("T");
                assertThat(response.getHoraLlegada()).isEqualTo(LocalTime.of(8, 12));
                assertThat(response.getJustificacionNota()).isNull();
            })
            .verifyComplete();
    }

    @Test
    void updateAttendanceAppliesChangesAndIncrementsVersion() {
        repository.save(existingAttendance(7L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("T")
            .horaLlegada(LocalTime.of(8, 12))
            .motivoCambio("Corrección de llegada")
            .changedBy("admin-1")
            .changedByRole("DEVELOPER")
            .build();

        StepVerifier.create(service.updateAttendance(7L, command))
            .assertNext(response -> {
                assertThat(response.getEstado()).isEqualTo("T");
                assertThat(response.getHoraLlegada()).isEqualTo(LocalTime.of(8, 12));
            })
            .verifyComplete();

        StepVerifier.create(repository.findById(7L))
            .assertNext(saved -> assertThat(saved.getVersion()).isEqualTo(1))
            .verifyComplete();

        assertThat(auditRepository.records).hasSize(1);
        assertThat(auditRepository.records.get(1L).getAction()).isEqualTo("UPDATE");
    }

    @Test
    void updateAttendanceAllowsOwnerTeacher() {
        repository.save(existingAttendance(9L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("F")
            .motivoCambio("Corrección del profesor")
            .changedBy("teacher-1")
            .changedByRole("TEACHER")
            .build();

        StepVerifier.create(service.updateAttendance(9L, command))
            .assertNext(response -> assertThat(response.getEstado()).isEqualTo("F"))
            .verifyComplete();
    }

    @Test
    void updateAttendanceRejectsNonOwnerTeacher() {
        repository.save(existingAttendance(10L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("F")
            .changedBy("teacher-2")
            .changedByRole("TEACHER")
            .build();

        StepVerifier.create(service.updateAttendance(10L, command))
            .expectError(ForbiddenException.class)
            .verify();
    }

    @Test
    void updateAttendanceAllowsSecretaryRole() {
        repository.save(existingAttendance(11L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("F")
            .motivoCambio("Corrección de secretaría")
            .changedBy("secretary-1")
            .changedByRole("SECRETARIA")
            .build();

        StepVerifier.create(service.updateAttendance(11L, command))
            .assertNext(response -> assertThat(response.getEstado()).isEqualTo("F"))
            .verifyComplete();
    }

    @Test
    void updateAttendanceAllowsAdminRole() {
        repository.save(existingAttendance(12L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("F")
            .motivoCambio("Corrección administrativa")
            .changedBy("admin-1")
            .changedByRole("ADMIN")
            .build();

        StepVerifier.create(service.updateAttendance(12L, command))
            .assertNext(response -> assertThat(response.getEstado()).isEqualTo("F"))
            .verifyComplete();
    }

    @Test
    void updateAttendanceRequiresReasonWhenStatusChanges() {
        repository.save(existingAttendance(13L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("F")
            .changedBy("admin-1")
            .changedByRole("ADMIN")
            .build();

        StepVerifier.create(service.updateAttendance(13L, command))
            .expectErrorMatches(error -> error instanceof BusinessException
                && error.getMessage().equals("El motivo del cambio es obligatorio cuando se corrige la asistencia"))
            .verify();
    }

    @Test
    void getAuditByAttendanceIdReturnsAuditRecords() {
        repository.save(existingAttendance(14L)).block();

        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
            .estado("F")
            .motivoCambio("Corrección administrativa")
            .changedBy("admin-1")
            .changedByRole("ADMIN")
            .build();

        service.updateAttendance(14L, command).block();

        StepVerifier.create(service.getAuditByAttendanceId(14L))
            .assertNext(audit -> {
                assertThat(audit.getAttendanceId()).isEqualTo(14L);
                assertThat(audit.getPreviousEstado()).isEqualTo("A");
                assertThat(audit.getNewEstado()).isEqualTo("F");
                assertThat(audit.getMotivoCambio()).isEqualTo("Corrección administrativa");
            })
            .verifyComplete();
    }

    @Test
    void registerAttendanceRejectsFutureDate() {
        RegisterAttendanceCommand command = RegisterAttendanceCommand.builder()
            .estudianteId("student-1")
            .claseId("class-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.now().plusDays(1))
            .anioLectivo(2026)
            .estado("A")
            .build();

        StepVerifier.create(service.registerAttendance(command))
            .expectError(BusinessException.class)
            .verify();
    }

    @Test
    void getAttendanceByDateRangeRejectsInvalidRange() {
        StepVerifier.create(service.getAttendanceByDateRange(
                "student-1",
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 5)))
            .expectError(BusinessException.class)
            .verify();
    }

    private Attendance existingAttendance(Long id) {
        return Attendance.builder()
            .id(id)
            .estudianteId("student-1")
            .claseId("class-1")
            .profesorId("teacher-1")
            .registradoPor("teacher-1")
            .fecha(LocalDate.of(2026, 6, 5))
            .anioLectivo(2026)
            .estado("A")
            .registradoEn(LocalDateTime.now())
            .creadoEn(LocalDateTime.now())
            .actualizadoEn(LocalDateTime.now())
            .version(0)
            .build();
    }

    private static class InMemoryAttendanceRepository implements AttendanceRepositoryPort {
        private final Map<Long, Attendance> records = new HashMap<>();
        private long sequence = 0;

        @Override
        public Mono<Attendance> save(Attendance attendance) {
            if (attendance.getId() == null) {
                attendance.setId(++sequence);
            }
            records.put(attendance.getId(), attendance);
            return Mono.just(attendance);
        }

        @Override
        public Mono<Attendance> findById(Long id) {
            return Mono.justOrEmpty(records.get(id));
        }

        @Override
        public Flux<Attendance> findByEstudianteId(String estudianteId) {
            return Flux.fromIterable(records.values())
                .filter(record -> estudianteId.equals(record.getEstudianteId()));
        }

        @Override
        public Flux<Attendance> findByClaseIdAndFecha(String claseId, LocalDate fecha) {
            return Flux.fromIterable(records.values())
                .filter(record -> claseId.equals(record.getClaseId()) && fecha.equals(record.getFecha()));
        }

        @Override
        public Flux<Attendance> findByEstudianteIdAndFechaBetween(String estudianteId, LocalDate startDate, LocalDate endDate) {
            return Flux.fromIterable(records.values())
                .filter(record -> estudianteId.equals(record.getEstudianteId()))
                .filter(record -> !record.getFecha().isBefore(startDate) && !record.getFecha().isAfter(endDate));
        }

        @Override
        public Mono<Boolean> existsByEstudianteIdAndClaseIdAndFecha(String estudianteId, String claseId, LocalDate fecha) {
            return Flux.fromIterable(records.values())
                .any(record -> estudianteId.equals(record.getEstudianteId())
                    && claseId.equals(record.getClaseId())
                    && fecha.equals(record.getFecha()));
        }

        @Override
        public Mono<Long> countBySessionId(Long sessionId) {
            return Flux.fromIterable(records.values())
                .filter(record -> Objects.equals(record.getSessionId(), sessionId))
                .count();
        }

        @Override
        public Flux<Attendance> findByFecha(LocalDate fecha) {
            return Flux.fromIterable(records.values())
                .filter(record -> fecha.equals(record.getFecha()));
        }

        @Override
        public Flux<com.vg.attendance.application.port.in.dto.DailyActivityCount> countDailySince(LocalDate since) {
            return Flux.empty();
        }

        @Override
        public Flux<com.vg.attendance.application.port.in.dto.HourlyFlowCount> countHourlyToday(LocalDate fecha) {
            return Flux.empty();
        }

        @Override
        public Flux<Attendance> findRecentAttendanceByStudent(String estudianteId) {
            return findByEstudianteId(estudianteId).take(30);
        }
    }

    private static class InMemoryAttendanceSessionRepository implements AttendanceSessionRepositoryPort {
        private final Map<Long, AttendanceSession> records = new HashMap<>();
        private long sequence = 0;

        @Override
        public Mono<AttendanceSession> save(AttendanceSession session) {
            if (session.getId() == null) {
                session.setId(++sequence);
            }
            records.put(session.getId(), session);
            return Mono.just(session);
        }

        @Override
        public Mono<AttendanceSession> findByClaseIdAndFecha(String claseId, LocalDate fecha) {
            return Flux.fromIterable(records.values())
                .filter(session -> claseId.equals(session.getClaseId()) && fecha.equals(session.getFecha()))
                .next();
        }

    }

    private static class NoopNotificationPort implements NotificationPort {
        @Override
        public Mono<Void> notifyAbsenceOrLate(Attendance attendance) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> notifyThreeFullAbsenceDays(String estudianteId, int absenceDays) {
            return Mono.empty();
        }
    }

    private static class InMemoryAttendanceAuditRepository implements AttendanceAuditRepositoryPort {
        private final Map<Long, AttendanceAudit> records = new HashMap<>();
        private long sequence = 0;

        @Override
        public Mono<AttendanceAudit> save(AttendanceAudit audit) {
            if (audit.getId() == null) {
                audit.setId(++sequence);
            }
            records.put(audit.getId(), audit);
            return Mono.just(audit);
        }

        @Override
        public Flux<AttendanceAudit> findByAttendanceId(Long attendanceId) {
            return Flux.fromIterable(records.values())
                .filter(record -> Objects.equals(record.getAttendanceId(), attendanceId));
        }
    }
}
