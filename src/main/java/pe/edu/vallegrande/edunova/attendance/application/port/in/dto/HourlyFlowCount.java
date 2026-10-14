package pe.edu.vallegrande.edunova.attendance.application.port.in.dto;

/** Agregado por hora de llegada (KPI flujo de ingreso del dashboard). */
public record HourlyFlowCount(int hora, long total) {
}
