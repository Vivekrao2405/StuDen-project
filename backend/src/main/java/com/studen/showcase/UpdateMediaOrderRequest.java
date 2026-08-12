package com.studen.showcase;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record UpdateMediaOrderRequest(@NotEmpty(message = "Media order is required") List<UUID> mediaIds) {
}
