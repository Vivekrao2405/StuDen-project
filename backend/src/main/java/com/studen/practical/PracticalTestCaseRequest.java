package com.studen.practical;

import jakarta.validation.constraints.NotBlank;

public record PracticalTestCaseRequest(
        @NotBlank(message = "Input is required") String input,
        @NotBlank(message = "Expected output is required") String expectedOutput,
        boolean hidden,
        int displayOrder) {
}
