package com.studen.marketplace;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record UpdateServiceMediaOrderRequest(@NotEmpty(message = "Media order is required") List<UUID> mediaIds) {
}
