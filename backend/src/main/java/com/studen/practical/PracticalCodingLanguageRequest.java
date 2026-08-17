package com.studen.practical;

import jakarta.validation.constraints.NotNull;

public record PracticalCodingLanguageRequest(
        @NotNull(message = "Language is required") CodingLanguage language,
        String starterCode) {
}
