package com.studen.practical;

import java.util.UUID;

public record PracticalCodingLanguageResponse(UUID id, CodingLanguage language, String starterCode) {

    public static PracticalCodingLanguageResponse from(PracticalCodingLanguage entity) {
        return new PracticalCodingLanguageResponse(entity.getId(), entity.getLanguage(), entity.getStarterCode());
    }
}
