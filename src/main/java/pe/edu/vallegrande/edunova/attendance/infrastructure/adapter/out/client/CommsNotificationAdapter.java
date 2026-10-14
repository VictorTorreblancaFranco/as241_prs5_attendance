package pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client;

import pe.edu.vallegrande.edunova.attendance.application.port.out.NotificationPort;
import pe.edu.vallegrande.edunova.attendance.domain.model.Attendance;
import pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto.ParentStudentLinkResponse;
import pe.edu.vallegrande.edunova.attendance.infrastructure.adapter.out.client.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

        // TODOS los apoderados del estudiante (padre y madre), no solo el primero.
        return userClient.getGuardiansByStudentId(attendance.getEstudianteId())
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(link -> notifyGuardian(attendance, link, tipo))
                .then()
                .onErrorResume(error -> {
                    log.error("Notificacion de asistencia NO enviada a comms: asistenciaId={} estudiante={} tipo={} motivo={}",
                            attendance.getId(), attendance.getEstudianteId(), tipo, error.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> notifyThreeFullAbsenceDays(String estudianteId, int absenceDays,
                                                 LocalDate finRacha, String profesorId) {
        // La clave usa el fin de la racha, no hoy: si el docente registra las faltas
        // con un día de retraso, la racha sigue siendo la misma y no se duplica el aviso.
        String claveRacha = (finRacha != null ? finRacha : LocalDate.now()).toString().replace("-", "");
        String frase = MensajesFaltaAcumulada.frase();
        return userClient.getGuardiansByStudentId(estudianteId)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(link -> {
                    UUID parentId = parseUuid(link.getParentId());
                    if (parentId == null) {
                        log.error("Falta acumulada sin notificar: parentId invalido '{}' para estudiante {}",
                                link.getParentId(), estudianteId);
                        return Mono.empty();
                    }
                    return userClient.getUserById(estudianteId)
                            .defaultIfEmpty(UserResponse.builder().id(estudianteId).build())
                            .map(student -> {
                                Map<String, Object> request = new LinkedHashMap<>();
                                request.put("destinatarioId", parentId.toString());
                                putStudentId(request, estudianteId);
                                request.put("tipo", "falta_acumulada");
                                request.put("titulo", "Falta acumulada de " + fullName(student));
                                // El mensaje base explica el hecho; la frase al azar invita
                                // a conversar, para que no se sienta un aviso automático más.
                                request.put("mensaje", "Su hijo ha acumulado " + absenceDays
                                        + " faltas consecutivas. " + frase);
                                request.put("canal", "ambos");
                                // Es el aviso de asistencia más serio: entra al push del
                                // apoderado (prioridad máxima) y también a su conversación
                                // con el profesor que registró la falta.
                                request.put("prioridad", "urgente");
                                putSenderId(request, profesorId);
                                request.put("idempotencyKey", "falta3-" + estudianteId + "-" + claveRacha + "-" + parentId);
                                return request;
                            })
                            .flatMap(request -> sendNotification(request, "falta_acumulada", parentId));
                })
                .then()
                .onErrorResume(error -> {
                    log.error("Falta acumulada NO notificada a comms: estudiante={} dias={} motivo={}",
                            estudianteId, absenceDays, error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> notifyGuardian(Attendance attendance, ParentStudentLinkResponse link, String tipo) {
        UUID parentId = parseUuid(link.getParentId());
        if (parentId == null) {
            log.error("Asistencia {} sin notificar: parentId invalido '{}' estudiante {}",
                    attendance.getId(), link.getParentId(), attendance.getEstudianteId());
            return Mono.empty();
        }
        return buildRequest(attendance, link, tipo, parentId)
                .flatMap(request -> sendNotification(request, tipo, parentId));
    }

    private Mono<Map<String, Object>> buildRequest(
            Attendance attendance,
            ParentStudentLinkResponse link,
            String tipo,
            UUID parentId
    ) {
        return userClient.getUserById(attendance.getEstudianteId())
                .defaultIfEmpty(UserResponse.builder().id(attendance.getEstudianteId()).build())
                .map(student -> {
                    Map<String, Object> request = new LinkedHashMap<>();
                    request.put("destinatarioId", parentId.toString());
                    putStudentId(request, attendance.getEstudianteId());
                    request.put("tipo", tipo);
                    request.put("titulo", tituloFor(tipo, fullName(student)));
                    request.put("mensaje", buildMensaje(attendance, student, tipo));
                    // "asistencia" solo a Bandeja/chat (sin push); F/T/J siguen avisando por push.
                    request.put("canal", "asistencia".equals(tipo) ? "web" : "ambos");
                    request.put("prioridad", "normal");
                    putSenderId(request, attendance.getProfesorId());
                    // Evita duplicados si se re-guarda la misma asistencia.
                    request.put("idempotencyKey",
                            "att-" + attendance.getId() + "-" + tipo + "-" + parentId);
                    return request;
                });
    }

    private Mono<Void> sendNotification(Map<String, Object> request, String tipo, UUID parentId) {
        return commsWebClient.post()
                .uri("/api/notifications/send")
                .header("X-Internal-Request", "gateway")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Notificacion de asistencia enviada a comms: tipo={} destinatario={}",
                        tipo, parentId))
                .then();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** studentId invalido (datos de prueba) tiraria 400 en comms: se omite el campo. */
    private static void putStudentId(Map<String, Object> request, String estudianteId) {
        UUID studentId = parseUuid(estudianteId);
        if (studentId != null) {
            request.put("studentId", studentId.toString());
        }
    }

    /** Emisor real (profesor de la clase) para que el chat quede profesor ↔ apoderado. */
    private static void putSenderId(Map<String, Object> request, String profesorId) {
        UUID senderId = parseUuid(profesorId);
        if (senderId != null) {
            request.put("senderUserId", senderId.toString());
        }
    }

    private String tipoFor(String status) {
        if (status == null) {
            return null;
        }

        return switch (status.toUpperCase()) {
            case "A" -> "asistencia";
            case "F" -> "falta";
            case "T" -> "tardanza";
            case "J" -> "falta_justificada";
            default -> null;
        };
    }

    /** Título legible para Bandeja/chat: "Falta de Juan Pérez" en vez del tipo crudo. */
    private static String tituloFor(String tipo, String nombre) {
        return switch (tipo) {
            case "asistencia" -> "Asistencia de " + nombre;
            case "falta" -> "Falta de " + nombre;
            case "tardanza" -> "Tardanza de " + nombre;
            case "falta_justificada" -> "Falta justificada de " + nombre;
            default -> tipo;
        };
    }

    private String buildMensaje(Attendance attendance, UserResponse student, String tipo) {
        String nombre = fullName(student);
        String fecha = attendance.getFecha() != null ? attendance.getFecha().toString() : "";

        return switch (tipo) {
            case "asistencia" -> "Su hijo " + nombre + " asistió hoy (" + fecha + ").";
            case "falta" -> "Su hijo " + nombre + " falta hoy (" + fecha + ").";
            case "tardanza" -> {
                String hora = attendance.getHoraLlegada() != null ? attendance.getHoraLlegada().toString() : "";
                yield "Su hijo " + nombre + " llega tarde hoy (" + fecha + " " + hora + ").";
            }
            case "falta_justificada" -> {
                String nota = attendance.getJustificacionNota() != null ? attendance.getJustificacionNota() : "";
                yield "Falta justificada de " + nombre + " (" + fecha + "): " + nota;
            }
            default -> "Notificacion de asistencia de " + nombre + " (" + fecha + ").";
        };
    }

    private String fullName(UserResponse user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? "Estudiante" : fullName;
    }
}
