package com.studen.practical.execution;

// RUN: student clicked "Run" -- public test cases only, never finalizes the attempt.
// SUBMIT: the authoritative final-submission execution -- full test set (public + hidden),
// feeds PracticalAttemptService.submit's automated-scoring path.
public enum ExecutionJobKind {
    RUN,
    SUBMIT
}
