package com.studen.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.studen.questionbank.Difficulty;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DifficultyDistributionCalculatorTest {

    @Test
    void twentyQuestions_defaultRatios_matchesSpecExample() {
        Map<Difficulty, Integer> result = DifficultyDistributionCalculator.compute(20, 0.3, 0.5, 0.2);

        assertThat(result.get(Difficulty.EASY)).isEqualTo(6);
        assertThat(result.get(Difficulty.MEDIUM)).isEqualTo(10);
        assertThat(result.get(Difficulty.HARD)).isEqualTo(4);
        assertThat(result.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(20);
    }

    @Test
    void alwaysSumsToTotal_evenWhenRatiosDontDivideEvenly() {
        for (int total = 1; total <= 37; total++) {
            Map<Difficulty, Integer> result = DifficultyDistributionCalculator.compute(total, 0.3, 0.5, 0.2);
            int sum = result.values().stream().mapToInt(Integer::intValue).sum();
            assertThat(sum).as("total=%d", total).isEqualTo(total);
        }
    }

    @Test
    void zeroTotal_returnsAllZero() {
        Map<Difficulty, Integer> result = DifficultyDistributionCalculator.compute(0, 0.3, 0.5, 0.2);

        assertThat(result.values()).allMatch(v -> v == 0);
    }

    @Test
    void everyBucketNonNegative() {
        Map<Difficulty, Integer> result = DifficultyDistributionCalculator.compute(7, 0.3, 0.5, 0.2);

        assertThat(result.values()).allMatch(v -> v >= 0);
    }
}
