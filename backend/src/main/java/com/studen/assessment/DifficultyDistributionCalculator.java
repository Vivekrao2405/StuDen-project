package com.studen.assessment;

import com.studen.questionbank.Difficulty;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a total question count + difficulty ratios (e.g. 30/50/20) into concrete per-difficulty
 * counts that always sum to exactly {@code total}. Pure/stateless so it's trivially unit-testable
 * and reusable by any future assessment type that needs a difficulty split — never reimplemented
 * per-caller. Flooring each bucket then handing the rounding remainder to the buckets with the
 * largest fractional remainder (ties broken by enum declaration order) keeps the result
 * deterministic for a given input.
 */
public final class DifficultyDistributionCalculator {

    private DifficultyDistributionCalculator() {
    }

    public static Map<Difficulty, Integer> compute(int total, double easyRatio, double mediumRatio, double hardRatio) {
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        Map<Difficulty, Double> ratios = new EnumMap<>(Difficulty.class);
        ratios.put(Difficulty.EASY, easyRatio);
        ratios.put(Difficulty.MEDIUM, mediumRatio);
        ratios.put(Difficulty.HARD, hardRatio);

        Map<Difficulty, Double> raw = new EnumMap<>(Difficulty.class);
        Map<Difficulty, Integer> floors = new EnumMap<>(Difficulty.class);
        int flooredSum = 0;
        for (Difficulty difficulty : Difficulty.values()) {
            double value = total * ratios.getOrDefault(difficulty, 0.0);
            raw.put(difficulty, value);
            int floor = (int) Math.floor(value);
            floors.put(difficulty, floor);
            flooredSum += floor;
        }

        int remainder = total - flooredSum;
        List<Difficulty> byFractionDesc = raw.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue() - Math.floor(b.getValue()), a.getValue() - Math.floor(a.getValue())))
                .map(Map.Entry::getKey)
                .toList();
        for (int i = 0; i < remainder; i++) {
            Difficulty difficulty = byFractionDesc.get(i % byFractionDesc.size());
            floors.merge(difficulty, 1, Integer::sum);
        }
        return floors;
    }
}
