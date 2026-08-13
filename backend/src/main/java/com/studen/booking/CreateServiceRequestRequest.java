package com.studen.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// No requesterId/providerId field exists here at all — the requester is always derived from the
// authenticated principal (see ServiceRequestController) and the provider is always derived from
// the loaded ServiceListing (see ServiceRequestService.createRequest). This structurally rules
// out the impersonation vectors client-supplied ids would otherwise open up.
public record CreateServiceRequestRequest(

        @NotNull(message = "A service is required")
        UUID serviceId,

        @NotBlank(message = "Please describe what you need")
        @Size(min = 20, max = 2000, message = "Description must be between 20 and 2000 characters")
        String description,

        @FutureOrPresent(message = "Expected delivery date can't be in the past")
        LocalDate requestedDeliveryDate,

        @Positive(message = "Budget must be a positive amount")
        Integer proposedBudget,

        @Valid
        @Size(max = 5, message = "You can add up to 5 reference links")
        List<ServiceRequestLinkRequest> links) {
}
