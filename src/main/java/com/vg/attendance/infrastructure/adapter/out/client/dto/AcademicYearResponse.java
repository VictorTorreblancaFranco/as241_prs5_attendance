package com.vg.attendance.infrastructure.adapter.out.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcademicYearResponse {
    private Integer id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
}
