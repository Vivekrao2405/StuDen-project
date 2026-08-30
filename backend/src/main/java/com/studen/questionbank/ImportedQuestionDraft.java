package com.studen.questionbank;

import java.util.List;

// One question extracted from an uploaded Markdown import file — never persisted directly.
// `errors` is populated by MarkdownQuestionParser at parse time and re-validated (never trusted
// as-is) by QuestionImportService right before the final import transaction, since the admin can
// edit any field in the Preview screen between parse and confirm.
public record ImportedQuestionDraft(
        int index,
        String questionText,
        QuestionType questionType,
        Difficulty difficulty,
        String explanation,
        // Exactly ONE hierarchical tag string, e.g. "python-sets-operators" — same rule as
        // QuestionRequest.tag, never an array/list of tags.
        String tag,
        List<ImportedOptionDraft> options,
        List<String> errors) {

    public boolean valid() {
        return errors == null || errors.isEmpty();
    }
}
