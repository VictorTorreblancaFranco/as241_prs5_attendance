package com.vg.attendance.domain.service;

import com.vg.attendance.domain.exception.BusinessException;
import com.vg.attendance.domain.model.Attendance;
import com.vg.attendance.domain.service.impl.AttendanceDomainServiceImpl;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceDomainServiceImplTest {

    private final AttendanceDomainServiceImpl service = new AttendanceDomainServiceImpl();

    @Test
    void validateAttendanceRejectsLateStatusWithoutArrivalTime() {
        Attendance attendance = Attendance.builder()
            .estado("T")
            .build();

        StepVerifier.create(service.validateAttendance(attendance))
            .expectErrorMatches(error -> error instanceof BusinessException
                && error.getMessage().equals("La tardanza requiere hora de llegada"))
            .verify();
    }

    @Test
    void validateAttendanceRejectsJustifiedStatusWithoutNote() {
        Attendance attendance = Attendance.builder()
            .estado("J")
            .justificacionNota(" ")
            .build();

        StepVerifier.create(service.validateAttendance(attendance))
            .expectErrorMatches(error -> error instanceof BusinessException
                && error.getMessage().equals("La justificación requiere una nota"))
            .verify();
    }

    @Test
    void validateAttendanceAllowsLateStatusWithOptionalJustificationNote() {
        Attendance attendance = Attendance.builder()
            .estado("T")
            .horaLlegada(LocalTime.of(8, 20))
            .justificacionNota("Llegó tarde con sustento")
            .build();

        StepVerifier.create(service.validateAttendance(attendance))
            .expectNext(attendance)
            .verifyComplete();
    }

    @Test
    void validateAttendanceRejectsAbsentStatusWithJustificationNote() {
        Attendance attendance = Attendance.builder()
            .estado("F")
            .justificacionNota("Debe ser J para justificar falta")
            .build();

        StepVerifier.create(service.validateAttendance(attendance))
            .expectErrorMatches(error -> error instanceof BusinessException
                && error.getMessage().equals("Solo una falta justificada o tardanza puede tener nota de justificación"))
            .verify();
    }

    @ParameterizedTest(name = "arrival={0}, start={1} -> {2} late minutes")
    @CsvSource({
        "08:15, 08:00, 15",
        "07:55, 08:00, 0",
        "08:00, 08:00, 0"
    })
    void calculateLateMinutesEvaluatesDifferentArrivalScenarios(
            LocalTime arrivalTime, LocalTime classStartTime, int expectedMinutes) {
        assertThat(service.calculateLateMinutes(arrivalTime, classStartTime))
            .isEqualTo(expectedMinutes);
    }
}
