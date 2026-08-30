package com.studen.questionbank;

import com.studen.common.exception.InvalidRequestException;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin bulk import: Upload → parse (in memory only, see MarkdownQuestionParser) → resolve each
 * question's skill/duplicate status against the current database (read-only) → Preview → admin
 * edits/removes rows client-side → confirmImport persists everything in one transaction. Never
 * writes partial rows — {@link #confirmImport} either creates every question or none.
 */
@Service
public class QuestionImportService {

    // Comfortably fits a 500+ question bank as plain text; guards against unbounded upload input.
    // Deliberately smaller than the shared app-wide multipart ceiling (application.yml, sized for
    // video uploads) rather than raising that shared limit for every other endpoint.
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_QUESTIONS_PER_IMPORT = 1000;

    private final MarkdownQuestionParser parser;
    private final QuestionBankService questionBankService;
    private final SkillRepository skillRepository;

    public QuestionImportService(MarkdownQuestionParser parser, QuestionBankService questionBankService,
            SkillRepository skillRepository) {
        this.parser = parser;
        this.questionBankService = questionBankService;
        this.skillRepository = skillRepository;
    }

    public ImportParseResponse parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A Markdown file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidRequestException("File is too large (max 5MB)");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new InvalidRequestException("Only .md (Markdown) files are supported");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }

        List<ImportedQuestionDraft> questions = parser.parse(content);
        if (questions.size() > MAX_QUESTIONS_PER_IMPORT) {
            throw new InvalidRequestException("Too many questions in one file (max " + MAX_QUESTIONS_PER_IMPORT + ")");
        }

        return ImportParseResponse.of(filename, resolveSkillsAndDuplicates(questions));
    }

    // Read-only: resolves each question's own "### SKILL" (if present) against existing skills —
    // never auto-created, an unmatched name is simply left unresolved (see ImportedQuestionDraft
    // javadoc for why that's not a blocking error by itself) — and flags any question whose
    // (resolved skill, question text) pair already exists in the Question Bank, reusing the exact
    // same normalized-text duplicate check the single-question create flow already uses, rather
    // than inventing a parallel ID-based uniqueness system.
    private List<ImportedQuestionDraft> resolveSkillsAndDuplicates(List<ImportedQuestionDraft> drafts) {
        List<ImportedQuestionDraft> result = new ArrayList<>();
        for (ImportedQuestionDraft draft : drafts) {
            ImportedQuestionDraft updated = draft;
            if (draft.skillName() != null && draft.skillId() == null) {
                Optional<Skill> skill = skillRepository.findByNormalizedName(normalize(draft.skillName()));
                if (skill.isPresent()) {
                    updated = updated.withSkill(skill.get().getName(), skill.get().getId());
                }
            }
            if (updated.skillId() != null && !updated.questionText().isBlank()) {
                DuplicateWarningResponse duplicate = questionBankService.checkDuplicate(updated.skillId(), updated.questionText());
                if (duplicate != null) {
                    updated = updated.withDuplicate(true)
                            .appendError("Duplicate: a question with this text already exists for this skill (existing: \""
                                    + duplicate.existingQuestionText() + "\")");
                }
            }
            result.add(updated);
        }
        return result;
    }

    // Single transaction for the whole batch (QuestionBankService.create is itself
    // @Transactional, which joins this method's transaction rather than opening a new one) — if
    // any question fails re-validation partway through, everything already created in this call
    // rolls back, so an import can never leave half-created questions behind.
    @Transactional
    public ImportConfirmResponse confirmImport(UUID userId, ImportConfirmRequest request) {
        List<ImportedQuestionDraft> questions = request.questions();
        for (ImportedQuestionDraft draft : questions) {
            if (!draft.valid()) {
                throw new InvalidRequestException(
                        "Question " + draft.index() + " has unresolved errors and can't be imported: "
                                + String.join("; ", draft.errors()));
            }
            if (draft.skillId() == null && request.skillId() == null) {
                throw new InvalidRequestException(
                        "Question " + draft.index() + " has no resolvable skill — select a skill above or fix its SKILL field");
            }
        }

        // Defensive re-check against the database as it stands right now — the admin may have sat
        // on the Preview screen for a while, or another admin may have imported the same question
        // in the meantime. Never trust the client-supplied `valid`/`duplicate` flags blindly for
        // the one thing that can go stale between parse and confirm.
        for (ImportedQuestionDraft draft : questions) {
            UUID effectiveSkillId = draft.skillId() != null ? draft.skillId() : request.skillId();
            DuplicateWarningResponse duplicate = questionBankService.checkDuplicate(effectiveSkillId, draft.questionText());
            if (duplicate != null) {
                throw new InvalidRequestException("Question " + draft.index()
                        + " now duplicates an existing question — re-run Parse to refresh the preview");
            }
        }

        List<UUID> createdIds = new ArrayList<>();
        for (ImportedQuestionDraft draft : questions) {
            UUID effectiveSkillId = draft.skillId() != null ? draft.skillId() : request.skillId();
            List<QuestionOptionRequest> options = new ArrayList<>();
            List<ImportedOptionDraft> draftOptions = draft.options();
            for (int i = 0; i < draftOptions.size(); i++) {
                ImportedOptionDraft option = draftOptions.get(i);
                options.add(new QuestionOptionRequest(option.optionText(), i, option.isCorrect()));
            }
            QuestionRequest questionRequest = new QuestionRequest(effectiveSkillId, request.topicId(),
                    draft.questionText(), draft.questionType(), draft.difficulty(), draft.explanation(), null,
                    draft.tag(), options);
            createdIds.add(questionBankService.create(userId, questionRequest).id());
        }

        return new ImportConfirmResponse(createdIds.size(), createdIds);
    }

    // Best-effort, per-question — deliberately NOT one all-or-nothing transaction like
    // confirmImport: each question's DRAFT -> REVIEW -> PUBLISHED transition is independent, so
    // one question failing publish-time validation (e.g. a Template A import with no explanation,
    // which publish() requires) must not undo every other question that already published fine.
    // Reuses QuestionBankService's existing submit-for-review/publish transitions and validation
    // rather than a separate bulk-specific path.
    public ImportPublishResponse publishImported(UUID reviewerId, List<UUID> questionIds) {
        int publishedCount = 0;
        List<ImportPublishFailure> failures = new ArrayList<>();
        for (UUID id : questionIds) {
            String preview = null;
            try {
                QuestionResponse current = questionBankService.get(id);
                preview = current.questionText();
                if (current.status() == QuestionStatus.PUBLISHED) {
                    publishedCount++;
                    continue;
                }
                if (current.status() == QuestionStatus.DRAFT) {
                    questionBankService.submitForReview(id);
                }
                questionBankService.publish(id, reviewerId);
                publishedCount++;
            } catch (RuntimeException e) {
                failures.add(new ImportPublishFailure(id, preview, e.getMessage()));
            }
        }
        return new ImportPublishResponse(publishedCount, failures);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
