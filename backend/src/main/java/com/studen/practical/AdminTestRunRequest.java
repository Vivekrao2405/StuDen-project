package com.studen.practical;

import jakarta.validation.constraints.NotBlank;

// language is required for CODING assessments, ignored (must be null) for SQL -- sourceCode is
// either the source code or the raw SQL query depending on the assessment's practicalType.
public record AdminTestRunRequest(CodingLanguage language, @NotBlank(message = "Source is required") String sourceCode) {
}
