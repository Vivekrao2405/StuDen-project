package com.studen.integrity;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses the optional {@code integrityPolicy} object out of
 * {@code PracticalAssessment.configurationJson} -- same defensive try/catch-and-default pattern
 * as {@code PracticalAttemptService#isOrderedSqlComparison}. Any missing field, malformed JSON,
 * or absent key falls back to {@link IntegrityPolicy#PERMISSIVE_DEFAULT} so existing assessments
 * (created before this phase) behave exactly as before.
 */
@Component
public class IntegrityPolicyResolver {

    private final ObjectMapper objectMapper;

    public IntegrityPolicyResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IntegrityPolicy resolve(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return IntegrityPolicy.PERMISSIVE_DEFAULT;
        }
        try {
            JsonNode root = objectMapper.readTree(configurationJson);
            JsonNode policy = root.get("integrityPolicy");
            if (policy == null || !policy.isObject()) {
                return IntegrityPolicy.PERMISSIVE_DEFAULT;
            }
            return new IntegrityPolicy(
                    boolOrDefault(policy.get("allowCopy"), true),
                    boolOrDefault(policy.get("allowPaste"), true),
                    boolOrDefault(policy.get("allowCut"), true),
                    boolOrDefault(policy.get("requireFullscreen"), false));
        } catch (Exception e) {
            return IntegrityPolicy.PERMISSIVE_DEFAULT;
        }
    }

    private boolean boolOrDefault(JsonNode node, boolean defaultValue) {
        return node != null && node.isBoolean() ? node.asBoolean() : defaultValue;
    }
}
