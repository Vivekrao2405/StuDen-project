package com.studen.questionbank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionOptionRequest(

        @NotBlank(message = "Option text is required")
        @Size(max = 500, message = "Option text must be at most 500 characters")
        String optionText,

        int displayOrder,

        boolean isCorrect) {
}
