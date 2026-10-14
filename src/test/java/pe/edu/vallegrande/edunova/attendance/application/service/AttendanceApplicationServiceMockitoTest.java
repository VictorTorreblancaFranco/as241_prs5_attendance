package pe.edu.vallegrande.edunova.attendance.application.service;

import pe.edu.vallegrande.edunova.attendance.application.port.in.command.RegisterAttendanceCommand;
import pe.edu.vallegrande.edunova.attendance.application.port.in.command.UpdateAttendanceCommand;
import pe.edu.vallegrande.edunova.attendance.application.port.out.AttendanceAuditRepositoryPort;
import pe.edu.vallegrande.edunova.attendance.application.port.out.AttendanceRepositoryPort;
import pe.edu.vallegrande.edunova.attendance.application.port.out.AttendanceSessionRepositoryPort;
import pe.edu.vallegrande.edunova.attendance.application.port.out.NotificationPort;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ConflictException;
import pe.edu.vallegrande.edunova.attendance.domain.exception.ForbiddenException;
import pe.edu.vallegrande.edunova.attendance.domain.model.Attendance;
import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceAudit;
import pe.edu.vallegrande.edunova.attendance.domain.model.AttendanceSession;
import pe.edu.vallegrande.edunova.attendance.domain.service.AttendanceDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceApplicationServiceMockitoTest {

    @Mock
    private AttendanceRepositoryPort attendanceRepository;
    @Mock
    private AttendanceAuditRepositoryPort auditRepository;
    @Mock
    private AttendanceSessionRepositoryPort sessionRepository;
    @Mock
    private AttendanceDomainService domainService;
    @Mock
    private NotificationPort notificationPort;

    private AttendanceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceApplicationService(
                attendanceRepository,
                auditRepository,
                sessionRepository,
                domainService,
                notificationPort);
    }

    @Test
    void registerAttendanceShouldCreateSessionAuditAndNotificationWhenCommandIsValid() {
        RegisterAttendanceCommand command = validRegisterCommand();
        AttendanceSession createdSession = AttendanceSession.builder()
                .id(99L)
                .claseId(command.getClaseId())
                .fecha(command.getFecha())
                .totalEstudiantes(12)
                .registrosGuardados(0)
                .estado("DRAFT")
                .build();

        when(attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                command.getEstudianteId(), command.getClaseId(), command.getFecha()))
                .thenReturn(Mono.just(false));
        when(sessionRepository.findByClaseIdAndFecha(command.getClaseId(), command.getFecha()))
                .thenReturn(Mono.empty(), Mono.just(createdSession));
        when(sessionRepository.save(any(AttendanceSession.class)))
                .thenReturn(Mono.just(createdSession));
        when(domainService.validateAttendance(any(Attendance.class)))
                .thenAnswer(invocation -> Mono.just(invocation.<Attendance>getArgument(0)));
        when(attendanceRepository.save(any(Attendance.class)))
                .thenAnswer(invocation -> {
                    Attendance attendance = invocation.getArgument(0, Attendance.class);
                    attendance.setId(1L);
                    return Mono.just(attendance);
                });
        when(auditRepository.save(any(AttendanceAudit.class)))
                .thenAnswer(invocation -> Mono.just(invocation.<AttendanceAudit>getArgument(0)));
        when(attendanceRepository.countBySessionId(99L)).thenReturn(Mono.just(1L));
        when(notificationPort.notifyAbsenceOrLate(any(Attendance.class))).thenReturn(Mono.empty());
        when(domainService.getStatusDescription("A")).thenReturn("Asistió");

        StepVerifier.create(service.registerAttendance(command))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getSessionId()).isEqualTo(99L);
                    assertThat(response.getEstado()).isEqualTo("A");
                    assertThat(response.getEstadoNombre()).isEqualTo("Asistió");
                })
                .verifyComplete();

        ArgumentCaptor<AttendanceAudit> auditCaptor = ArgumentCaptor.forClass(AttendanceAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CREATE");
        assertThat(auditCaptor.getValue().getMotivoCambio()).isEqualTo("Registro inicial de asistencia");
        verify(notificationPort).notifyAbsenceOrLate(any(Attendance.class));
    }

    @Test
    void registerAttendanceShouldRejectDuplicateStudentClassAndDate() {
        RegisterAttendanceCommand command = validRegisterCommand();
        when(attendanceRepository.existsByEstudianteIdAndClaseIdAndFecha(
                command.getEstudianteId(), command.getClaseId(), command.getFecha()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.registerAttendance(command))
                .expectError(ConflictException.class)
                .verify();

        verify(attendanceRepository, never()).save(any(Attendance.class));
        verify(auditRepository, never()).save(any(AttendanceAudit.class));
    }

    @Test
    void updateAttendanceShouldSaveAuditWhenDirectorJustifiesAbsence() {
        Attendance existing = existingAttendance();
        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
                .estado("J")
                .justificacionNota("Padre presentó sustento documentado")
                .justificacionFotoUrl("https://cdn.edunova/evidencia.jpg")
                .motivoCambio("Validación de justificación")
                .changedBy("director-1")
                .changedByRole("DIRECTOR")
                .build();

        when(attendanceRepository.findById(7L)).thenReturn(Mono.just(existing));
        when(domainService.validateAttendance(any(Attendance.class)))
                .thenAnswer(invocation -> Mono.just(invocation.<Attendance>getArgument(0)));
        when(attendanceRepository.save(any(Attendance.class)))
                .thenAnswer(invocation -> Mono.just(invocation.<Attendance>getArgument(0)));
        when(auditRepository.save(any(AttendanceAudit.class)))
                .thenAnswer(invocation -> Mono.just(invocation.<AttendanceAudit>getArgument(0)));
        when(notificationPort.notifyAbsenceOrLate(any(Attendance.class))).thenReturn(Mono.empty());
        when(domainService.getStatusDescription("J")).thenReturn("Justificado");

        StepVerifier.create(service.updateAttendance(7L, command))
                .assertNext(response -> {
                    assertThat(response.getEstado()).isEqualTo("J");
                    assertThat(response.getJustificacionNota()).isEqualTo("Padre presentó sustento documentado");
                    assertThat(response.getJustificacionFotoUrl()).isEqualTo("https://cdn.edunova/evidencia.jpg");
                })
                .verifyComplete();

        ArgumentCaptor<AttendanceAudit> auditCaptor = ArgumentCaptor.forClass(AttendanceAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getPreviousEstado()).isEqualTo("F");
        assertThat(auditCaptor.getValue().getNewEstado()).isEqualTo("J");
        assertThat(auditCaptor.getValue().getChangedByRole()).isEqualTo("DIRECTOR");
    }

    @Test
    void updateAttendanceShouldRejectTeacherThatDoesNotOwnClass() {
        UpdateAttendanceCommand command = UpdateAttendanceCommand.builder()
                .estado("A")
                .motivoCambio("Intento de corrección")
                .changedBy("teacher-2")
                .changedByRole("TEACHER")
                .build();

        when(attendanceRepository.findById(7L)).thenReturn(Mono.just(existingAttendance()));

        StepVerifier.create(service.updateAttendance(7L, command))
                .expectError(ForbiddenException.class)
                .verify();

        verify(attendanceRepository, never()).save(any(Attendance.class));
        verify(auditRepository, never()).save(any(AttendanceAudit.class));
    }

    private RegisterAttendanceCommand validRegisterCommand() {
        return RegisterAttendanceCommand.builder()
                .estudianteId("student-1")
                .claseId("class-1")
                .profesorId("teacher-1")
                .registradoPor("teacher-1")
                .registradoPorRole("TEACHER")
                .fecha(LocalDate.now().minusDays(1))
                .anioLectivo(1)
                .totalEstudiantesSesion(12)
                .estado("A")
                .build();
    }

    private Attendance existingAttendance() {
        return Attendance.builder()
                .id(7L)
                .sessionId(99L)
                .estudianteId("student-1")
                .claseId("class-1")
                .profesorId("teacher-1")
                .registradoPor("teacher-1")
                .fecha(LocalDate.now().minusDays(1))
                .anioLectivo(1)
                .estado("F")
                .registradoEn(LocalDateTime.now().minusHours(2))
                .creadoEn(LocalDateTime.now().minusHours(2))
                .actualizadoEn(LocalDateTime.now().minusHours(2))
                .version(0)
                .build();
    }
}
