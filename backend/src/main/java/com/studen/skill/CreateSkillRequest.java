package com.studen.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSkillRequest(

        @NotBlank(message = "Skill name is required")
        @Size(max = 100, message = "Skill name must be at most 100 characters")
        String name,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must be at most 50 characters")
        String category) {
}
