package com.studen.practical;

import java.util.UUID;

// Deliberately has no `hidden` field at all (unlike PracticalTestCaseResponse) — this record is
// only ever built from PracticalTestCase rows already filtered to hidden==false, and having no
// field to leak is a stronger guarantee than trusting every call site to filter correctly.
public record StudentTestCaseView(UUID id, String input, String expectedOutput, int displayOrder) {

    public static StudentTestCaseView from(PracticalTestCase entity) {
        return new StudentTestCaseView(entity.getId(), entity.getInput(), entity.getExpectedOutput(),
                entity.getDisplayOrder());
    }
}
