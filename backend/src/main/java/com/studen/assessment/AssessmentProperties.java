package com.studen.assessment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The "trusted backend configuration source" the spec requires (§6/§23) — question count,
 * difficulty distribution, and time limit all come from here, never from client input. Env-var
 * overridable per environment like every other {@code app.*} block, never hardcoded at call sites.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.assessment")
public class AssessmentProperties {

    private int defaultQuestionCount = 30;
    private double easyRatio = 0.3;
    private double mediumRatio = 0.5;
    private double hardRatio = 0.2;
    private int timeLimitMinutes = 30;

    public int timeLimitSeconds() {
        return timeLimitMinutes * 60;
    }
}
