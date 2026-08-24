package com.studen.resource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.learning")
public class LearningProperties {

    // "Don't flood the student" (spec §9) — the max resources shown per weak-area group.
    private int maxResourcesPerGroup = 6;
}
