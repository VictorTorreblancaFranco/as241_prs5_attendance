package pe.edu.vallegrande.edunova.attendance.application.port.in.dto;

import java.time.LocalDate;

/** Agregado diario de registros de asistencia (KPI actividad del dashboard). */
public record DailyActivityCount(LocalDate fecha, long total) {
}
