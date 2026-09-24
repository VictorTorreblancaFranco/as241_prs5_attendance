package com.vg.attendance.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceBulkRequest {
    @NotBlank(message = "El profesor es obligatorio")
    private String profesorId;

    @NotNull(message = "La fecha de asistencia es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "El año lectivo es obligatorio")
    @Min(value = 2000, message = "El año lectivo no es válido")
    @Max(value = 2200, message = "El año lectivo no es válido")
    private Integer anioLectivo;

    @Valid
    @NotEmpty(message = "Debe enviar al menos una asistencia")
    @Size(max = 1000, message = "No se pueden registrar más de 1000 asistencias por solicitud")
    private List<AttendanceBulkItemRequest> asistencias;
}
