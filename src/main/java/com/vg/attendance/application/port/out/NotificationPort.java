package com.vg.attendance.application.port.out;

import com.vg.attendance.domain.model.Attendance;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface NotificationPort {
    Mono<Void> notifyAbsenceOrLate(Attendance attendance);

    /**
     * Aviso de racha de faltas: se dispara cuando se alcanza el umbral y lleva la
     * fecha de la última falta de la racha (para que la clave de idempotencia sea
     * la de ESA racha y no la del día en que se detecta) y el profesor que la
     * registró, que queda como emisor del aviso.
     */
    Mono<Void> notifyThreeFullAbsenceDays(String estudianteId, int absenceDays,
                                          LocalDate finRacha, String profesorId);
}
