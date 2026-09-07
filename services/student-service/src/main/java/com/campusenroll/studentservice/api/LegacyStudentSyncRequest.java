package com.campusenroll.studentservice.api;

import com.campusenroll.studentservice.domain.LegacyStudentSyncCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LegacyStudentSyncRequest(
        @NotBlank @Size(max = 32) String studentNo,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 32) String departmentCode,
        @NotBlank @Size(max = 128) String departmentName,
        @NotBlank @Size(max = 32) String majorCode,
        @NotBlank @Size(max = 128) String majorName,
        @Min(1900) @Max(3000) int gradeYear,
        @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED|GRADUATED|WITHDRAWN") String status) {

    public LegacyStudentSyncCommand toCommand(String legacyStudentId) {
        return new LegacyStudentSyncCommand(
                legacyStudentId.trim(),
                studentNo.trim(),
                name.trim(),
                departmentCode.trim(),
                departmentName.trim(),
                majorCode.trim(),
                majorName.trim(),
                gradeYear,
                status);
    }
}
