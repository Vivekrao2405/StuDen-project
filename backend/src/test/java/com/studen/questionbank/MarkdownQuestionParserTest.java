package com.studen.questionbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

// Pure unit tests — MarkdownQuestionParser has no Spring/DB dependency, so these run without a
// Spring context. Covers both the original "## Q1" template and the newer fixed-form "## QUESTION"
// template (Template B) side by side, since the parser must auto-detect and support both.
class MarkdownQuestionParserTest {

    private final MarkdownQuestionParser parser = new MarkdownQuestionParser();

    private String templateBQuestion(String id, String skill, String difficulty, String tags, String questionText,
            String options, String answer, String explanation) {
        return """
                ## QUESTION

                ### ID
                %s

                ### SKILL
                %s

                ### DIFFICULTY
                %s

                ### TAGS
                %s

                ### QUESTION_TEXT
                %s

                ### OPTIONS
                %s

                ### ANSWER
                %s

                ### EXPLANATION
                %s

                ### END
                """.formatted(id, skill, difficulty, tags, questionText, options, answer, explanation);
    }

    private String fourOptions() {
        return "A. class\nB. struct\nC. define\nD. type";
    }

    private String validQuestion(String id) {
        return templateBQuestion(id, "java", "easy", "java-basics, java-syntax",
                "Which keyword is used to declare a class?", fourOptions(), "A", "The `class` keyword declares a class.");
    }

    @Test
    void templateB_singleValidQuestion_detectsAllFields() {
        List<ImportedQuestionDraft> drafts = parser.parse(validQuestion("java-basics-001"));

        assertThat(drafts).hasSize(1);
        ImportedQuestionDraft draft = drafts.get(0);
        assertThat(draft.valid()).isTrue();
        assertThat(draft.externalId()).isEqualTo("java-basics-001");
        assertThat(draft.skillName()).isEqualTo("java");
        assertThat(draft.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(draft.tag()).isEqualTo("java-basics"); // first of "TAGS", single-tag rule preserved
        assertThat(draft.questionType()).isEqualTo(QuestionType.MCQ_SINGLE);
        assertThat(draft.options()).hasSize(4);
        assertThat(draft.options().get(0).isCorrect()).isTrue(); // "A"
        assertThat(draft.explanation()).contains("`class`");
    }

    @Test
    void templateB_25Questions_allDetectedAndValid() {
        StringBuilder file = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            file.append(validQuestion("java-basics-%03d".formatted(i)));
        }
        List<ImportedQuestionDraft> drafts = parser.parse(file.toString());

        assertThat(drafts).hasSize(25);
        assertThat(drafts).allMatch(ImportedQuestionDraft::valid);
        assertThat(drafts.get(24).index()).isEqualTo(25);
    }

    @Test
    void templateB_200Questions_allDetectedAndValid() {
        StringBuilder file = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            file.append(validQuestion("java-basics-%03d".formatted(i)));
        }
        List<ImportedQuestionDraft> drafts = parser.parse(file.toString());

        assertThat(drafts).hasSize(200);
        assertThat(drafts).allMatch(ImportedQuestionDraft::valid);
        assertThat(drafts).extracting(ImportedQuestionDraft::externalId).doesNotHaveDuplicates();
    }

    @Test
    void templateB_missingExplanation_reportsError() {
        String md = templateBQuestion("java-basics-001", "java", "easy", "java-basics",
                "Which keyword is used to declare a class?", fourOptions(), "A", "");

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).isFalse();
        assertThat(draft.errors()).contains("Explanation is required");
    }

    @Test
    void templateB_invalidDifficulty_reportsError() {
        String md = templateBQuestion("java-basics-001", "java", "impossible", "java-basics",
                "Which keyword is used to declare a class?", fourOptions(), "A", "Because.");

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).isFalse();
        assertThat(draft.errors()).contains("Difficulty must be Easy, Medium, or Hard");
    }

    @Test
    void templateB_invalidAnswerLetter_reportsError() {
        String md = templateBQuestion("java-data-types-147", "java", "medium", "java-data-types",
                "Which of these is not a primitive type?", fourOptions(), "E", "Explanation text.");

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).isFalse();
        assertThat(draft.errors()).contains("Answer \"E\" is invalid. Expected A, B, C, or D");
    }

    @Test
    void templateB_missingOption_reportsExactlyFourRequired() {
        String md = templateBQuestion("java-basics-001", "java", "easy", "java-basics",
                "Which keyword is used to declare a class?", "A. class\nB. struct\nC. define", "A", "Because.");

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).isFalse();
        assertThat(draft.errors()).contains("Exactly 4 options (A, B, C, D) are required");
    }

    @Test
    void templateB_fiveOptions_reportsExactlyFourRequired() {
        String md = templateBQuestion("java-basics-001", "java", "easy", "java-basics",
                "Which keyword is used to declare a class?", "A. class\nB. struct\nC. define\nD. type\nE. record",
                "A", "Because.");

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).isFalse();
        assertThat(draft.errors()).contains("Exactly 4 options (A, B, C, D) are required");
    }

    @Test
    void templateB_duplicateIdWithinFile_reportsErrorOnSecondOccurrence() {
        String md = validQuestion("java-basics-001") + validQuestion("java-basics-001");

        List<ImportedQuestionDraft> drafts = parser.parse(md);

        assertThat(drafts.get(0).valid()).isTrue();
        assertThat(drafts.get(1).valid()).isFalse();
        assertThat(drafts.get(1).duplicate()).isTrue();
        assertThat(drafts.get(1).errors()).anyMatch(e -> e.contains("Duplicate ID \"java-basics-001\""));
    }

    @Test
    void templateB_codeBlockContainingHeadingsAndBackticks_preservedAndNeverSplitsBlock() {
        String md = """
                ## QUESTION

                ### ID
                java-code-001

                ### SKILL
                java

                ### DIFFICULTY
                medium

                ### TAGS
                java-basics

                ### QUESTION_TEXT
                What is the output?

                ```java
                // ## QUESTION this looks like a heading but is inside a fence
                ### Not a real section either
                int x = 10;
                System.out.println(x + 5);
                ```

                ### OPTIONS
                A. 15
                B. 10
                C. Compile error
                D. Runtime error

                ### ANSWER
                A

                ### EXPLANATION
                `x + 5` evaluates to 15.

                ### END

                ## QUESTION

                ### ID
                java-code-002

                ### SKILL
                java

                ### DIFFICULTY
                easy

                ### TAGS
                java-basics

                ### QUESTION_TEXT
                Second question, to prove the fenced fake headers above didn't leak a phantom block.

                ### OPTIONS
                A. one
                B. two
                C. three
                D. four

                ### ANSWER
                B

                ### EXPLANATION
                Because.

                ### END
                """;

        List<ImportedQuestionDraft> drafts = parser.parse(md);

        assertThat(drafts).hasSize(2); // the fenced "## QUESTION" / "###" lines must not start new blocks/sections
        ImportedQuestionDraft first = drafts.get(0);
        assertThat(first.valid()).describedAs(String.join("; ", first.errors())).isTrue();
        assertThat(first.questionText()).contains("```java");
        assertThat(first.questionText()).contains("## QUESTION this looks like a heading but is inside a fence");
        assertThat(first.questionText()).contains("### Not a real section either");
        assertThat(first.options().get(0).optionText()).isEqualTo("15");
        assertThat(drafts.get(1).externalId()).isEqualTo("java-code-002");
    }

    @Test
    void templateB_crlfLineEndings_parsesIdenticallyToLf() {
        String lf = validQuestion("java-basics-001");
        String crlf = lf.replace("\n", "\r\n");

        List<ImportedQuestionDraft> fromLf = parser.parse(lf);
        List<ImportedQuestionDraft> fromCrlf = parser.parse(crlf);

        assertThat(fromCrlf).hasSize(1);
        assertThat(fromCrlf.get(0).valid()).isTrue();
        assertThat(fromCrlf.get(0).questionText()).isEqualTo(fromLf.get(0).questionText());
        assertThat(fromCrlf.get(0).options()).isEqualTo(fromLf.get(0).options());
    }

    @Test
    void templateB_blankLinesAndTrailingWhitespaceAroundSections_tolerated() {
        String md = "## QUESTION   \n\n\n###   ID   \n\n   java-basics-001   \n\n\n### SKILL\njava\n\n"
                + "### DIFFICULTY\n\neasy\n\n### TAGS\njava-basics\n\n### QUESTION_TEXT\nWhich keyword?\n\n"
                + "### OPTIONS\nA. class   \nB. struct\nC. define\nD. type\n\n### ANSWER\n  A  \n\n"
                + "### EXPLANATION\nBecause.\n\n### END\n";

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).describedAs(String.join("; ", draft.errors())).isTrue();
        assertThat(draft.externalId()).isEqualTo("java-basics-001");
        assertThat(draft.options().get(0).optionText()).isEqualTo("class"); // trailing spaces trimmed
    }

    @Test
    void templateB_longExplanation_preservedInFull() {
        String longExplanation = "This is a very long explanation. ".repeat(200).trim();
        String md = templateBQuestion("java-basics-001", "java", "easy", "java-basics",
                "Which keyword is used to declare a class?", fourOptions(), "A", longExplanation);

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).isTrue();
        assertThat(draft.explanation()).isEqualTo(longExplanation);
    }

    @Test
    void templateA_originalFormat_stillParsesUnchanged() {
        String md = """
                ## Q1

                ### Question
                What is the output of the following code?

                ```python
                a = {1, 2, 3}
                b = {2, 3, 4}
                print(a & b)
                ```

                ### Options
                A. {1}
                B. {2, 3}
                C. {1, 2, 3, 4}
                D. {4}

                ### Answer
                B

                ### Explanation
                The & operator returns the intersection of two sets.

                ### Difficulty
                Easy

                ### Tag
                python-sets-operators
                """;

        ImportedQuestionDraft draft = parser.parse(md).get(0);

        assertThat(draft.valid()).describedAs(String.join("; ", draft.errors())).isTrue();
        assertThat(draft.externalId()).isNull(); // Template A never has an ID
        assertThat(draft.skillName()).isNull(); // Template A never has a SKILL
        assertThat(draft.tag()).isEqualTo("python-sets-operators");
        assertThat(draft.questionType()).isEqualTo(QuestionType.MCQ_SINGLE);
        assertThat(draft.options().get(1).isCorrect()).isTrue(); // "B"
    }

    @Test
    void templateA_trueFalseAndMultiAnswerInference_stillWorks() {
        String trueFalse = """
                ## Q1

                ### Question
                Booleans in Python are a subtype of int.

                ### Options
                A. True
                B. False

                ### Answer
                A

                ### Difficulty
                Medium

                ### Tag
                python-basics
                """;
        String multiAnswer = """
                ## Q2

                ### Question
                Which of these create an empty set?

                ### Options
                A. set()
                B. {}
                C. set([])
                D. frozenset()

                ### Answer
                A, C

                ### Difficulty
                Medium

                ### Tag
                python-sets
                """;

        assertThat(parser.parse(trueFalse).get(0).questionType()).isEqualTo(QuestionType.TRUE_FALSE);
        assertThat(parser.parse(multiAnswer).get(0).questionType()).isEqualTo(QuestionType.MCQ_MULTIPLE);
    }
}
