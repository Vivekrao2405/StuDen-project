package com.studen.education;

import java.time.Instant;
import java.util.UUID;

public record EducationResponse(
        UUID id,
        String degree,
        String fieldOfStudy,
        String institution,
        Integer startYear,
        Integer endYear,
        boolean current,
        Instant createdAt,
        Instant updatedAt) {

    public static EducationResponse from(Education education) {
        return new EducationResponse(
                education.getId(),
                education.getDegree(),
                education.getFieldOfStudy(),
                education.getInstitution(),
                education.getStartYear(),
                education.getEndYear(),
                education.isCurrent(),
                education.getCreatedAt(),
                education.getUpdatedAt());
    }
}
