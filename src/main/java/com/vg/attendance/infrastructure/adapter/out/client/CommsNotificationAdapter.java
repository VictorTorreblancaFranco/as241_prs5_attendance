package com.vg.attendance.infrastructure.adapter.out.client;

import com.vg.attendance.application.port.out.NotificationPort;
import com.vg.attendance.domain.model.Attendance;
import com.vg.attendance.infrastructure.adapter.out.client.dto.ParentStudentLinkResponse;
import com.vg.attendance.infrastructure.adapter.out.client.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class CommsNotificationAdapter implements NotificationPort {

    private final WebClient commsWebClient;
    private final UserClient userClient;

    public CommsNotificationAdapter(
            @Qualifier("commsWebClient") WebClient commsWebClient,
            UserClient userClient
    ) {
        this.commsWebClient = commsWebClient;
        this.userClient = userClient;
    }

    @Override
    public Mono<Void> notifyAbsenceOrLate(Attendance attendance) {
        String tipo = tipoFor(attendance.getEstado());
        if (tipo == null) {
            return Mono.empty();
        }

        return userClient.getGuardiansByStudentId(attendance.getEstudianteId())
                .next()
                .flatMap(link -> buildRequest(attendance, link, tipo))
                .flatMap(this::sendNotification)
                .onErrorResume(error -> {
                    log.warn("No se pudo notificar asistencia a comms: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> notifyThreeFullAbsenceDays(String estudianteId, int absenceDays) {
        return userClient.getGuardiansByStudentId(estudianteId)
                .next()
                .flatMap(link -> {
                    Map<String, Object> request = new LinkedHashMap<>();
                    request.put("destinatarioId", link.getParentId());
                    request.put("studentId", estudianteId);
                    request.put("tipo", "falta_acumulada");
                    request.put("mensaje", "Su hijo ha acumulado " + absenceDays + " faltas consecutivas.");
                    request.put("canal", "ambos");
                    request.put("prioridad", "alta");
                    return sendNotification(request);
                })
                .onErrorResume(error -> {
                    log.warn("No se pudo notificar falta acumulada a comms: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Map<String, Object>> buildRequest(
            Attendance attendance,
            ParentStudentLinkResponse link,
            String tipo
    ) {
        return userClient.getUserById(attendance.getEstudianteId())
                .defaultIfEmpty(UserResponse.builder().id(attendance.getEstudianteId()).build())
                .map(student -> {
                    Map<String, Object> request = new LinkedHashMap<>();
                    request.put("destinatarioId", link.getParentId());
                    request.put("studentId", attendance.getEstudianteId());
                    request.put("tipo", tipo);
                    request.put("mensaje", buildMensaje(attendance, student, tipo));
                    request.put("canal", "ambos");
                    request.put("prioridad", "normal");
                    return request;
                });
    }

    private Mono<Void> sendNotification(Map<String, Object> request) {
        return commsWebClient.post()
                .uri("/api/notifications/send")
                .header("X-Internal-Request", "gateway")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Notificación de asistencia enviada a comms"))
                .then();
    }

    private String tipoFor(String status) {
        if (status == null) {
            return null;
        }

        return switch (status.toUpperCase()) {
            case "F" -> "falta";
            case "T" -> "tardanza";
            case "J" -> "falta_justificada";
            default -> null;
        };
    }

    private String buildMensaje(Attendance attendance, UserResponse student, String tipo) {
        String nombre = fullName(student);
        String fecha = attendance.getFecha() != null ? attendance.getFecha().toString() : "";

        return switch (tipo) {
            case "falta" -> "Su hijo " + nombre + " falta hoy (" + fecha + ").";
            case "tardanza" -> {
                String hora = attendance.getHoraLlegada() != null ? attendance.getHoraLlegada().toString() : "";
                yield "Su hijo " + nombre + " llega tarde hoy (" + fecha + " " + hora + ").";
            }
            case "falta_justificada" -> {
                String nota = attendance.getJustificacionNota() != null ? attendance.getJustificacionNota() : "";
                yield "Falta justificada de " + nombre + " (" + fecha + "): " + nota;
            }
            default -> "Notificación de asistencia de " + nombre + " (" + fecha + ").";
        };
    }

    private String fullName(UserResponse user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? "Estudiante" : fullName;
    }
}
