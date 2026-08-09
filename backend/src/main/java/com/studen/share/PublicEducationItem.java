package com.studen.share;

import com.studen.education.Education;

public record PublicEducationItem(
        String degree,
        String fieldOfStudy,
        String institution,
        Integer startYear,
        Integer endYear,
        boolean current) {

    public static PublicEducationItem from(Education education) {
        return new PublicEducationItem(
                education.getDegree(),
                education.getFieldOfStudy(),
                education.getInstitution(),
                education.getStartYear(),
                education.getEndYear(),
                education.isCurrent());
    }
}
