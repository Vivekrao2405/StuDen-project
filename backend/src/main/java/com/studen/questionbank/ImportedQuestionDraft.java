package com.studen.questionbank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// One question extracted from an uploaded Markdown import file — never persisted directly.
// `errors` is populated by MarkdownQuestionParser at parse time and re-validated (never trusted
// as-is) by QuestionImportService right before the final import transaction, since the admin can
// edit any field in the Preview screen between parse and confirm.
//
// `externalId`/`skillName` come from the newer "## QUESTION" / "### ID" / "### SKILL" template
// (see MarkdownQuestionParser); both are null for the older "## Q1" template, which relies on the
// admin picking one skill for the whole batch in the Preview screen instead (ImportConfirmRequest
// .skillId). `skillId` is set once QuestionImportService resolves `skillName` against an existing
// Skill — deliberately NOT treated as a blocking error when unresolved/blank, since
// ImportConfirmRequest.skillId is always a valid fallback for it (see QuestionImportService).
public record ImportedQuestionDraft(
        int index,
        String externalId,
        String questionText,
        QuestionType questionType,
        Difficulty difficulty,
        String explanation,
        // Exactly ONE hierarchical tag string, e.g. "python-sets-operators" — same rule as
        // QuestionRequest.tag, never an array/list of tags. A "### TAGS" section with multiple
        // comma-separated values keeps only the first (see MarkdownQuestionParser).
        String tag,
        String skillName,
        UUID skillId,
        // True once flagged as a duplicate (same ID reused within the file, or the same question
        // text already exists in the Question Bank for the resolved skill) — always paired with a
        // matching entry in `errors`, so it never needs checking separately for import-readiness.
        boolean duplicate,
        List<ImportedOptionDraft> options,
        List<String> errors) {

    public boolean valid() {
        return errors == null || errors.isEmpty();
    }

    public ImportedQuestionDraft withErrors(List<String> newErrors) {
        return new ImportedQuestionDraft(index, externalId, questionText, questionType, difficulty, explanation, tag,
                skillName, skillId, duplicate, options, newErrors);
    }

    public ImportedQuestionDraft appendError(String message) {
        List<String> newErrors = new ArrayList<>(errors == null ? List.of() : errors);
        newErrors.add(message);
        return withErrors(newErrors);
    }

    public ImportedQuestionDraft withSkill(String resolvedSkillName, UUID resolvedSkillId) {
        return new ImportedQuestionDraft(index, externalId, questionText, questionType, difficulty, explanation, tag,
                resolvedSkillName, resolvedSkillId, duplicate, options, errors);
    }

    public ImportedQuestionDraft withDuplicate(boolean isDuplicate) {
        return new ImportedQuestionDraft(index, externalId, questionText, questionType, difficulty, explanation, tag,
                skillName, skillId, isDuplicate, options, errors);
    }
}
