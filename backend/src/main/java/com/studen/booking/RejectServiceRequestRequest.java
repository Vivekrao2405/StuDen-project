package com.studen.booking;

import jakarta.validation.constraints.Size;

// The whole body is optional (a client may POST /reject with no body at all, see
// ServiceRequestController) — reason itself is likewise optional, so no @NotBlank.
public record RejectServiceRequestRequest(

        @Size(max = 500, message = "Rejection reason must be at most 500 characters")
        String reason) {
}
