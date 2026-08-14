package com.studen.questionbank;

// DRAFT -> REVIEW -> PUBLISHED -> ARCHIVED. Only PUBLISHED questions are selectable by the
// future Assessment Engine (Phase 7.2). Transitions are enforced exclusively in
// QuestionBankService — never trust a client-supplied status directly.
public enum QuestionStatus {
    DRAFT,
    REVIEW,
    PUBLISHED,
    ARCHIVED
}
