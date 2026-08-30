package com.studen.questionbank;

import java.util.UUID;

public record ImportPublishFailure(UUID questionId, String questionTextPreview, String reason) {
}
