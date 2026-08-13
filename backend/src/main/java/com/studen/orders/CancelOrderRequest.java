package com.studen.orders;

import jakarta.validation.constraints.Size;

// The whole body is optional (a client may POST /cancel with no body at all, see
// OrderController) — reason itself is likewise optional, mirroring RejectServiceRequestRequest.
public record CancelOrderRequest(

        @Size(max = 500, message = "Cancellation reason must be at most 500 characters")
        String reason) {
}
