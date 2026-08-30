package com.studen.questionbank;

import java.util.List;
import java.util.UUID;

public record ImportConfirmResponse(int importedCount, List<UUID> questionIds) {
}
