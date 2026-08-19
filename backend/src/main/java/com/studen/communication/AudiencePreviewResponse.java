package com.studen.communication;

import java.util.List;

public record AudiencePreviewResponse(long count, List<String> sampleFirstNames) {
}
