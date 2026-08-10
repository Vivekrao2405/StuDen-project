package com.studen.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Phone must be a valid phone number")
        String phone) {
}
