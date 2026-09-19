package com.vg.attendance.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** KPI del dashboard: agregado diario de asistencia (sin datos personales). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDailyStatsResponse {
    private LocalDate fecha;
    private Integer total;
    private Integer present;
    private Integer late;
    private Integer absent;
    private Integer justified;
    /** % de llegadas a tiempo: present / (present + late). */
    private Double punctualityRate;
    /** % de asistencia efectiva: (present + late) / total. */
    private Double attendanceRate;
}
