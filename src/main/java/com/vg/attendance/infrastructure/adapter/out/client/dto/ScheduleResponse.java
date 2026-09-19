package com.vg.attendance.infrastructure.adapter.out.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.time.DayOfWeek;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {
    private Long id;
    private Long academicYearId;
    private Long gradeId;
    private Long sectionId;
    private Long courseId;
    private String teacherId;
    @JsonAlias({"spaceId", "classroomId"})
    private Long classroomId;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean active;
}
