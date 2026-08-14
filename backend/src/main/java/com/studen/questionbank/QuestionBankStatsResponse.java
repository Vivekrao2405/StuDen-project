package com.studen.questionbank;

import java.util.List;
import java.util.Map;

public record QuestionBankStatsResponse(long total, long draft, long review, long published, long archived) {

    public static QuestionBankStatsResponse from(List<QuestionRepository.StatusCount> counts) {
        Map<QuestionStatus, Long> byStatus = counts.stream()
                .collect(java.util.stream.Collectors.toMap(QuestionRepository.StatusCount::getStatus,
                        QuestionRepository.StatusCount::getTotal));
        long draft = byStatus.getOrDefault(QuestionStatus.DRAFT, 0L);
        long review = byStatus.getOrDefault(QuestionStatus.REVIEW, 0L);
        long published = byStatus.getOrDefault(QuestionStatus.PUBLISHED, 0L);
        long archived = byStatus.getOrDefault(QuestionStatus.ARCHIVED, 0L);
        return new QuestionBankStatsResponse(draft + review + published + archived, draft, review, published, archived);
    }
}
