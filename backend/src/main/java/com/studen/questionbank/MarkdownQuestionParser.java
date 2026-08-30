package com.studen.questionbank;

import java.util.ArrayList;
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
 * Parses the bulk-import Markdown format into in-memory {@link ImportedQuestionDraft}s — never
 * touches the database (see QuestionImportService for the Preview → confirm → transactional-save
 * split). One "## Q&lt;n&gt;" heading starts each question; "### Question / Options / Answer /
 * Explanation / Difficulty / Tag" subheadings delimit its fields, e.g.:
 *
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
 *
 * Question type is never a separate field — it's inferred (True/False options → TRUE_FALSE, more
 * than one answer letter → MCQ_MULTIPLE, otherwise MCQ_SINGLE), matching the exact format spelled
 * out for the importer.
 */
@Component
public class MarkdownQuestionParser {

    private static final Pattern QUESTION_HEADER = Pattern.compile("^##\\s+Q\\d+\\s*$");
    private static final Pattern SECTION_HEADER = Pattern.compile("^###\\s+(.+?)\\s*$");
    private static final Pattern OPTION_LINE = Pattern.compile("^([A-Za-z])[.)]\\s*(.+)$");
    private static final Pattern FENCE = Pattern.compile("^```");

    public List<ImportedQuestionDraft> parse(String markdown) {
        List<List<String>> blocks = splitIntoQuestionBlocks(markdown);
        List<ImportedQuestionDraft> drafts = new ArrayList<>();
        int index = 1;
        for (List<String> block : blocks) {
            drafts.add(parseBlock(index++, block));
        }
        return drafts;
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

        String questionText = section(sections, "question").trim();
        String answerRaw = section(sections, "answer").trim();
        String explanationRaw = section(sections, "explanation").trim();
        String difficultyRaw = section(sections, "difficulty").trim();
        String tagRaw = section(sections, "tag").trim();

        Map<Character, String> optionsByLetter = parseOptions(section(sections, "options"));
        Set<Character> answerLetters = parseAnswerLetters(answerRaw);
        Difficulty difficulty = parseDifficulty(difficultyRaw);

        List<String> errors = new ArrayList<>();
        if (questionText.isBlank()) {
            errors.add("Question text is required");
        }
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
        if (difficultyRaw.isBlank()) {
            errors.add("Difficulty is required");
        } else if (difficulty == null) {
            errors.add("Difficulty must be Easy, Medium, or Hard");
        }
        if (tagRaw.isBlank()) {
            errors.add("Tag is required");
        } else if (!QuestionValidationService.isValidTagFormat(tagRaw)) {
            errors.add("Tag must be a single hierarchical string (e.g. python-sets-operators), not multiple tags");
        }

        QuestionType questionType = inferQuestionType(optionsByLetter, answerLetters);
        List<ImportedOptionDraft> options = new ArrayList<>();
        for (Map.Entry<Character, String> entry : optionsByLetter.entrySet()) {
            options.add(new ImportedOptionDraft(entry.getValue(), answerLetters.contains(entry.getKey())));
        }

        return new ImportedQuestionDraft(index, questionText, questionType, difficulty,
                explanationRaw.isBlank() ? null : explanationRaw, tagRaw.isBlank() ? null : tagRaw, options, errors);
    }

    private String section(Map<String, StringBuilder> sections, String name) {
        StringBuilder value = sections.get(name);
        return value == null ? "" : value.toString();
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
