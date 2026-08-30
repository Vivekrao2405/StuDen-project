package com.studen.questionbank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parses bulk-import Markdown into in-memory {@link ImportedQuestionDraft}s — never touches the
 * database (see QuestionImportService for the Preview → confirm → transactional-save split, and
 * for the one DB-dependent step, skill/duplicate resolution, that necessarily happens after this
 * class returns). Two question templates are recognized, auto-detected per block so existing
 * files keep working unchanged:
 *
 * <p><b>Template A</b> (original, still supported) — one "## Q&lt;n&gt;" heading per question,
 * "### Question / Options / Answer / Explanation / Difficulty / Tag" subheadings:
 * <pre>
 * ## Q1
 *
 * ### Question
 * What is the output of the following code?
 *
 * ```python
 * a = {1, 2, 3}
 * ```
 *
 * ### Options
 * A. {1}
 * B. {2, 3}
 *
 * ### Answer
 * B
 *
 * ### Explanation
 * The &amp; operator returns the intersection of two sets.
 *
 * ### Difficulty
 * Easy
 *
 * ### Tag
 * python-sets-operators
 * </pre>
 * Question type is inferred (True/False options → TRUE_FALSE, more than one answer letter →
 * MCQ_MULTIPLE, otherwise MCQ_SINGLE) — this template has no separate type field.
 *
 * <p><b>Template B</b> (fixed-form MCQ, e.g. bulk language question banks) — every question
 * starts with a literal "## QUESTION" heading, and carries its own ID/skill/tags:
 * <pre>
 * ## QUESTION
 *
 * ### ID
 * java-basics-001
 *
 * ### SKILL
 * java
 *
 * ### DIFFICULTY
 * easy
 *
 * ### TAGS
 * java-basics, java-syntax
 *
 * ### QUESTION_TEXT
 * Which keyword is used to declare a class?
 *
 * ### OPTIONS
 * A. class
 * B. struct
 * C. define
 * D. type
 *
 * ### ANSWER
 * A
 *
 * ### EXPLANATION
 * The `class` keyword is used to declare a Java class.
 *
 * ### END
 * </pre>
 * Template B is always exactly 4 options (A–D), exactly one answer letter, and always
 * {@link QuestionType#MCQ_SINGLE} — matching the fixed format above (a "### END" marker, if
 * present, is ignored; the next "## QUESTION" heading is what actually ends a block). Only the
 * first tag in "### TAGS" is kept — see {@link ImportedQuestionDraft#tag()}.
 *
 * <p>A block is treated as Template B the moment it has an "### ID", "### SKILL", "### TAGS", or
 * "### QUESTION_TEXT" section — anything else falls back to Template A's rules unchanged, so nothing
 * about the original format's behavior (2+ options, True/False, multi-answer) regresses.
 */
@Component
public class MarkdownQuestionParser {

    private static final Pattern QUESTION_HEADER = Pattern.compile("^##\\s+(Q\\d+|QUESTION)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_HEADER = Pattern.compile("^###\\s+(.+?)\\s*$");
    private static final Pattern OPTION_LINE = Pattern.compile("^([A-Za-z])[.)]\\s*(.+)$");
    private static final Pattern FENCE = Pattern.compile("^```");
    private static final Set<Character> FOUR_OPTION_LETTERS = Set.of('A', 'B', 'C', 'D');

    public List<ImportedQuestionDraft> parse(String markdown) {
        List<List<String>> blocks = splitIntoQuestionBlocks(markdown);
        List<ImportedQuestionDraft> drafts = new ArrayList<>();
        int index = 1;
        for (List<String> block : blocks) {
            drafts.add(parseBlock(index++, block));
        }
        return flagDuplicateIdsWithinFile(drafts);
    }

    private List<List<String>> splitIntoQuestionBlocks(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        boolean inFence = false;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean isFenceLine = FENCE.matcher(trimmed).find();
            if (!inFence && !isFenceLine && QUESTION_HEADER.matcher(trimmed).matches()) {
                current = new ArrayList<>();
                blocks.add(current);
                continue;
            }
            if (isFenceLine) {
                inFence = !inFence;
            }
            if (current != null) {
                current.add(line);
            }
        }
        return blocks;
    }

    private ImportedQuestionDraft parseBlock(int index, List<String> lines) {
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        StringBuilder currentSection = null;
        boolean inFence = false;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean isFenceLine = FENCE.matcher(trimmed).find();
            Matcher header = (!inFence && !isFenceLine) ? SECTION_HEADER.matcher(trimmed) : null;
            if (header != null && header.matches()) {
                currentSection = new StringBuilder();
                sections.put(header.group(1).trim().toLowerCase(Locale.ROOT), currentSection);
                continue;
            }
            if (isFenceLine) {
                inFence = !inFence;
            }
            if (currentSection != null) {
                currentSection.append(line).append("\n");
            }
        }

        boolean isTemplateB = sections.containsKey("id") || sections.containsKey("skill")
                || sections.containsKey("tags") || sections.containsKey("question_text");

        String questionText = section(sections, sections.containsKey("question_text") ? "question_text" : "question").trim();
        String answerRaw = section(sections, "answer").trim();
        String explanationRaw = section(sections, "explanation").trim();
        String difficultyRaw = section(sections, "difficulty").trim();
        String externalId = blankToNull(section(sections, "id").trim());
        String skillNameRaw = blankToNull(section(sections, "skill").trim());

        List<String> tagList = parseTagList(sections);
        String tag = tagList.isEmpty() ? "" : tagList.get(0);

        Map<Character, String> optionsByLetter = parseOptions(section(sections, "options"));
        Set<Character> answerLetters = parseAnswerLetters(answerRaw);
        Difficulty difficulty = parseDifficulty(difficultyRaw);

        List<String> errors = new ArrayList<>();
        if (questionText.isBlank()) {
            errors.add("Question text is required");
        }

        QuestionType questionType;
        if (isTemplateB) {
            if (externalId == null) {
                errors.add("ID is required");
            }
            if (!optionsByLetter.keySet().equals(FOUR_OPTION_LETTERS)) {
                errors.add("Exactly 4 options (A, B, C, D) are required");
            }
            if (answerLetters.size() != 1) {
                errors.add("Answer must be exactly one letter (A, B, C, or D)");
            } else {
                char letter = answerLetters.iterator().next();
                if (!FOUR_OPTION_LETTERS.contains(letter)) {
                    errors.add("Answer \"" + letter + "\" is invalid. Expected A, B, C, or D");
                } else if (!optionsByLetter.containsKey(letter)) {
                    errors.add("Correct answer \"" + letter + "\" does not match any option");
                }
            }
            if (explanationRaw.isBlank()) {
                errors.add("Explanation is required");
            }
            questionType = QuestionType.MCQ_SINGLE;
        } else {
            if (optionsByLetter.size() < 2) {
                errors.add("At least 2 options are required");
            }
            if (answerLetters.isEmpty()) {
                errors.add("Answer is required");
            } else {
                for (Character letter : answerLetters) {
                    if (!optionsByLetter.containsKey(letter)) {
                        errors.add("Correct answer \"" + letter + "\" does not match any option");
                    }
                }
            }
            questionType = inferQuestionType(optionsByLetter, answerLetters);
        }

        if (difficultyRaw.isBlank()) {
            errors.add("Difficulty is required");
        } else if (difficulty == null) {
            errors.add("Difficulty must be Easy, Medium, or Hard");
        }
        if (tag.isBlank()) {
            errors.add("Tag is required");
        } else if (!QuestionValidationService.isValidTagFormat(tag)) {
            errors.add("Tag must be a single hierarchical string (e.g. python-sets-operators), not multiple tags");
        }

        List<ImportedOptionDraft> options = new ArrayList<>();
        for (Map.Entry<Character, String> entry : optionsByLetter.entrySet()) {
            options.add(new ImportedOptionDraft(entry.getValue(), answerLetters.contains(entry.getKey())));
        }

        return new ImportedQuestionDraft(index, externalId, questionText, questionType, difficulty,
                explanationRaw.isBlank() ? null : explanationRaw, tag.isBlank() ? null : tag, skillNameRaw, null,
                false, options, errors);
    }

    // Template B's "### ID" is only meaningful within one uploaded file (Question has no external
    // business-key column — see QuestionImportService for how DB-level duplicates are detected
    // instead, via the existing normalized-question-text check). Two questions reusing the same ID
    // is always a file-authoring mistake, so the second (and any later) occurrence is flagged.
    private List<ImportedQuestionDraft> flagDuplicateIdsWithinFile(List<ImportedQuestionDraft> drafts) {
        Map<String, Integer> firstSeenAtIndex = new HashMap<>();
        List<ImportedQuestionDraft> result = new ArrayList<>();
        for (ImportedQuestionDraft draft : drafts) {
            if (draft.externalId() != null) {
                Integer firstIndex = firstSeenAtIndex.putIfAbsent(draft.externalId(), draft.index());
                if (firstIndex != null) {
                    result.add(draft.withDuplicate(true)
                            .appendError("Duplicate ID \"" + draft.externalId() + "\" (already used by Question " + firstIndex + " in this file)"));
                    continue;
                }
            }
            result.add(draft);
        }
        return result;
    }

    private String section(Map<String, StringBuilder> sections, String name) {
        StringBuilder value = sections.get(name);
        return value == null ? "" : value.toString();
    }

    private String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    // "### TAGS" (Template B) is a comma-separated list; "### Tag" (Template A) is a single value.
    // Splitting either on "," is safe for both — a Template A value never legitimately contains one.
    private List<String> parseTagList(Map<String, StringBuilder> sections) {
        String raw = sections.containsKey("tags") ? section(sections, "tags") : section(sections, "tag");
        List<String> tags = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    // TreeMap keeps options ordered by letter (A, B, C, ...) regardless of the order lines happen
    // to appear in the file.
    private Map<Character, String> parseOptions(String optionsSection) {
        Map<Character, String> options = new TreeMap<>();
        for (String line : optionsSection.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = OPTION_LINE.matcher(trimmed);
            if (m.matches()) {
                options.put(Character.toUpperCase(m.group(1).charAt(0)), m.group(2).trim());
            }
        }
        return options;
    }

    private Set<Character> parseAnswerLetters(String answerRaw) {
        Set<Character> letters = new java.util.LinkedHashSet<>();
        for (char c : answerRaw.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetter(c)) {
                letters.add(c);
            }
        }
        return letters;
    }

    private Difficulty parseDifficulty(String raw) {
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "EASY" -> Difficulty.EASY;
            case "MEDIUM" -> Difficulty.MEDIUM;
            case "HARD" -> Difficulty.HARD;
            default -> null;
        };
    }

    private QuestionType inferQuestionType(Map<Character, String> optionsByLetter, Set<Character> answerLetters) {
        boolean isTrueFalse = optionsByLetter.size() == 2 && optionsByLetter.values().stream()
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("true", "false"));
        if (isTrueFalse) {
            return QuestionType.TRUE_FALSE;
        }
        return answerLetters.size() > 1 ? QuestionType.MCQ_MULTIPLE : QuestionType.MCQ_SINGLE;
    }
}
