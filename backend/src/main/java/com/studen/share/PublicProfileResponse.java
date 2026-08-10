package com.studen.share;

import java.util.List;

public record PublicProfileResponse(
        String slug,
        String profileUrl,
        String fullName,
        String profileImageUrl,
        String coverImageUrl,
        String headline,
        String about,
        String location,
        boolean availability,
        List<Object> skills,
        List<PublicEducationItem> education,
        List<PublicCertificateItem> certificates,
        List<Object> showcase,
        List<Object> services) {
}
