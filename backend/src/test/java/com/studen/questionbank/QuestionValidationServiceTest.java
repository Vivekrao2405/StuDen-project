package com.studen.questionbank;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studen.common.exception.InvalidRequestException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionValidationServiceTest {

    private final QuestionValidationService service = new QuestionValidationService();

    private QuestionRequest request(QuestionType type, Difficulty difficulty, String explanation,
            List<QuestionOptionRequest> options) {
        return new QuestionRequest(UUID.randomUUID(), null, "Some question text?", type, difficulty, explanation,
                null, null, options);
    }

    private QuestionOptionRequest option(String text, boolean correct) {
        return new QuestionOptionRequest(text, 0, correct);
    }

    @Test
    void mcqSingle_withExactlyOneCorrectOption_isValid() {
        QuestionRequest request = request(QuestionType.MCQ_SINGLE, Difficulty.EASY, "why",
                List.of(option("A", true), option("B", false)));
        assertThatCode(() -> service.validate(request, false)).doesNotThrowAnyException();
    }

    @Test
    void mcqSingle_withZeroCorrectOptions_throws() {
        QuestionRequest request = request(QuestionType.MCQ_SINGLE, Difficulty.EASY, "why",
                List.of(option("A", false), option("B", false)));
        assertThatThrownBy(() -> service.validate(request, false)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void mcqSingle_withTwoCorrectOptions_throws() {
        QuestionRequest request = request(QuestionType.MCQ_SINGLE, Difficulty.EASY, "why",
                List.of(option("A", true), option("B", true)));
        assertThatThrownBy(() -> service.validate(request, false)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void mcqSingle_withFewerThanTwoOptions_throws() {
        QuestionRequest request = request(QuestionType.MCQ_SINGLE, Difficulty.EASY, "why", List.of(option("A", true)));
        assertThatThrownBy(() -> service.validate(request, false)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void mcqMultiple_withTwoCorrectOptions_isValid() {
        QuestionRequest request = request(QuestionType.MCQ_MULTIPLE, Difficulty.MEDIUM, "why",
                List.of(option("A", true), option("B", false), option("C", true)));
        assertThatCode(() -> service.validate(request, false)).doesNotThrowAnyException();
    }

    @Test
    void mcqMultiple_withZeroCorrectOptions_throws() {
        QuestionRequest request = request(QuestionType.MCQ_MULTIPLE, Difficulty.MEDIUM, "why",
                List.of(option("A", false), option("B", false)));
        assertThatThrownBy(() -> service.validate(request, false)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void trueFalse_withExactlyTwoOptionsOneCorrect_isValid() {
        QuestionRequest request = request(QuestionType.TRUE_FALSE, Difficulty.EASY, "why",
                List.of(option("True", true), option("False", false)));
        assertThatCode(() -> service.validate(request, false)).doesNotThrowAnyException();
    }

    @Test
    void trueFalse_withThreeOptions_throws() {
        QuestionRequest request = request(QuestionType.TRUE_FALSE, Difficulty.EASY, "why",
                List.of(option("True", true), option("False", false), option("Maybe", false)));
        assertThatThrownBy(() -> service.validate(request, false)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void requireExplanation_whenBlank_throws() {
        QuestionRequest request = request(QuestionType.TRUE_FALSE, Difficulty.EASY, "   ",
                List.of(option("True", true), option("False", false)));
        assertThatThrownBy(() -> service.validate(request, true)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void requireExplanation_false_allowsBlankExplanation() {
        QuestionRequest request = request(QuestionType.TRUE_FALSE, Difficulty.EASY, null,
                List.of(option("True", true), option("False", false)));
        assertThatCode(() -> service.validate(request, false)).doesNotThrowAnyException();
    }
}
