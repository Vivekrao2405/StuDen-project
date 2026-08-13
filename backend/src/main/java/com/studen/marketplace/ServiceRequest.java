package com.studen.marketplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// No `status` field, deliberately: the only way a service reaches ACTIVE is the dedicated
// /publish endpoint, which runs full server-side validation first — this is what stops a client
// from posting straight to public visibility with missing/invalid data. `@Positive` on a
// nullable Integer only fires when a value is present, so a DRAFT with no price/delivery yet is
// still a valid request.
public record ServiceRequest(

        @NotBlank(message = "Service title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Category is required")
        MarketplaceCategory category,

        @Size(max = 255, message = "Location must be at most 255 characters")
        String location,

        Set<UUID> skillIds,

        @Size(max = 20, message = "You can list at most 20 deliverables")
        List<@NotBlank(message = "Deliverable text can't be blank") @Size(max = 300,
                message = "Each deliverable must be at most 300 characters") String> whatYoullReceive,

        @Positive(message = "Price must be greater than zero")
        Integer priceAmount,

        ServiceCurrency currency,

        @Positive(message = "Delivery time must be at least 1 day")
        @Max(value = 3650, message = "Delivery time must be at most 3650 days")
        Integer deliveryDays,

        Boolean available,

        Set<UUID> linkedProjectIds,

        @Valid
        List<ServiceLinkRequest> links) {
}
