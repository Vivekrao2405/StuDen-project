package com.studen.questionbank;

// TRUE_FALSE deliberately reuses the same QuestionOption structure as the MCQ types (modeled as
// a fixed "True"/"False" option pair) rather than a separate answer model — see
// QuestionValidationService. Future types (CODE, SHORT_ANSWER, FILL_IN_THE_BLANK, SCENARIO,
// MATCHING) can be added here without touching the Question/QuestionOption schema.
public enum QuestionType {
    MCQ_SINGLE,
    MCQ_MULTIPLE,
    TRUE_FALSE
}
