package com.studen.questionbank;

import java.util.List;

public record ImportPublishResponse(int publishedCount, List<ImportPublishFailure> failures) {
}
