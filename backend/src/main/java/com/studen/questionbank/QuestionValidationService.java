package com.studen.questionbank;

import com.studen.common.exception.InvalidRequestException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Per-question-type structural rules (spec §22/§9). Runs on every create/update, and again
 * (with {@code requireExplanation = true}) on publish, since a REVIEW question could have been
 * left incomplete — see QuestionBankService.publish.
 */
@Service
public class QuestionValidationService {

    private static final int MAX_QUESTION_TEXT_LENGTH = 2000;
    private static final int MAX_OPTION_TEXT_LENGTH = 500;

    // One hierarchical tag per question, e.g. "python-sets-operators" — alphanumeric segments
    // joined by single hyphens. Rejects arrays/lists reaching here as a comma- or
    // whitespace-separated string (a client working around the single-String DTO shape).
    private static final java.util.regex.Pattern TAG_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$");

    // Shared with MarkdownQuestionParser so the bulk importer rejects a malformed tag with the
    // exact same rule as manual creation/editing, rather than a second drifting definition.
    public static boolean isValidTagFormat(String tag) {
        return tag != null && !tag.isBlank() && !tag.contains(",") && !tag.contains(" ") && TAG_PATTERN.matcher(tag).matches();
    }

    public void validate(QuestionRequest request, boolean requireExplanation) {
        if (request.questionText() == null || request.questionText().isBlank()) {
            throw new InvalidRequestException("Question text is required");
        }
        if (request.questionText().length() > MAX_QUESTION_TEXT_LENGTH) {
            throw new InvalidRequestException("Question text must be at most " + MAX_QUESTION_TEXT_LENGTH + " characters");
        }
        if (request.skillId() == null) {
            throw new InvalidRequestException("Skill is required");
        }
        if (request.difficulty() == null) {
            throw new InvalidRequestException("Difficulty is required");
        }
        if (request.questionType() == null) {
            throw new InvalidRequestException("Question type is required");
        }
        if (requireExplanation && (request.explanation() == null || request.explanation().isBlank())) {
            throw new InvalidRequestException("An explanation is required before publishing");
        }
        if (request.tag() != null && !request.tag().isBlank() && (request.tag().contains(",") || request.tag().contains(" ")
                || !TAG_PATTERN.matcher(request.tag()).matches())) {
            throw new InvalidRequestException(
                    "Tag must be a single hierarchical string (e.g. python-sets-operators), not multiple tags");
        }

        List<QuestionOptionRequest> options = request.options() == null ? List.of() : request.options();
        for (QuestionOptionRequest option : options) {
            if (option.optionText() == null || option.optionText().isBlank()) {
                throw new InvalidRequestException("Option text can't be blank");
            }
            if (option.optionText().length() > MAX_OPTION_TEXT_LENGTH) {
                throw new InvalidRequestException("Option text must be at most " + MAX_OPTION_TEXT_LENGTH + " characters");
            }
        }

        long correctCount = options.stream().filter(QuestionOptionRequest::isCorrect).count();

        switch (request.questionType()) {
            case MCQ_SINGLE -> {
                if (options.size() < 2) {
                    throw new InvalidRequestException("MCQ_SINGLE questions require at least 2 options");
                }
                if (correctCount != 1) {
                    throw new InvalidRequestException("MCQ_SINGLE questions require exactly 1 correct option");
                }
            }
            case MCQ_MULTIPLE -> {
                if (options.size() < 2) {
                    throw new InvalidRequestException("MCQ_MULTIPLE questions require at least 2 options");
                }
                if (correctCount < 1) {
                    throw new InvalidRequestException("MCQ_MULTIPLE questions require at least 1 correct option");
                }
            }
            case TRUE_FALSE -> {
                if (options.size() != 2) {
                    throw new InvalidRequestException("TRUE_FALSE questions require exactly 2 options");
                }
                if (correctCount != 1) {
                    throw new InvalidRequestException("TRUE_FALSE questions require exactly 1 correct option");
                }
            }
        }
    }
}
