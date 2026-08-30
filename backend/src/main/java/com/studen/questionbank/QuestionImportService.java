package com.studen.questionbank;

import com.studen.common.exception.InvalidRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin bulk import: Upload → parse (in memory only, see MarkdownQuestionParser) → Preview →
 * admin edits/removes rows client-side → confirmImport persists everything in one transaction.
 * Never writes partial rows — {@link #confirmImport} either creates every question or none.
 */
@Service
public class QuestionImportService {

    private final MarkdownQuestionParser parser;
    private final QuestionBankService questionBankService;

    public QuestionImportService(MarkdownQuestionParser parser, QuestionBankService questionBankService) {
        this.parser = parser;
        this.questionBankService = questionBankService;
    }

    public ImportParseResponse parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A Markdown file is required");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            throw new InvalidRequestException("Only .md (Markdown) files are supported");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }

        List<ImportedQuestionDraft> questions = parser.parse(content);
        return ImportParseResponse.of(filename, questions);
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
        }

        List<UUID> createdIds = new ArrayList<>();
        for (ImportedQuestionDraft draft : questions) {
            List<QuestionOptionRequest> options = new ArrayList<>();
            List<ImportedOptionDraft> draftOptions = draft.options();
            for (int i = 0; i < draftOptions.size(); i++) {
                ImportedOptionDraft option = draftOptions.get(i);
                options.add(new QuestionOptionRequest(option.optionText(), i, option.isCorrect()));
            }
            QuestionRequest questionRequest = new QuestionRequest(request.skillId(), request.topicId(),
                    draft.questionText(), draft.questionType(), draft.difficulty(), draft.explanation(), null,
                    draft.tag(), options);
            createdIds.add(questionBankService.create(userId, questionRequest).id());
        }

        return new ImportConfirmResponse(createdIds.size(), createdIds);
    }
}
