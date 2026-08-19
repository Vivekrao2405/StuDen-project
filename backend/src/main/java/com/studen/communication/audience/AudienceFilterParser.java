package com.studen.communication.audience;

import com.studen.common.exception.InvalidRequestException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses the JSON filter tree the admin Filter Builder UI sends into a typed
 * {@link AudienceFilterNode}. Same defensive-tree-walking idiom as
 * {@code com.studen.integrity.IntegrityPolicyResolver}, except a malformed *audience* filter is a
 * genuine client error (400), not something to silently default away — an admin must never see a
 * recipient count for a filter that isn't the one they actually built.
 */
@Component
public class AudienceFilterParser {

    private final ObjectMapper objectMapper;

    public AudienceFilterParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AudienceFilterNode parse(String filterJson) {
        if (filterJson == null || filterJson.isBlank()) {
            return new AudienceFilterGroup(AudienceLogicOperator.AND, List.of());
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(filterJson);
        } catch (Exception e) {
            throw new InvalidRequestException("Audience filter is not valid JSON");
        }
        return parseNode(root);
    }

    private AudienceFilterNode parseNode(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            throw new InvalidRequestException("Each audience filter node must be a JSON object");
        }
        if (node.has("operator") && node.has("children")) {
            AudienceLogicOperator operator = enumOrThrow(AudienceLogicOperator.class, node.get("operator").asString(),
                    "operator");
            List<AudienceFilterNode> children = new ArrayList<>();
            for (Iterator<JsonNode> it = node.get("children").iterator(); it.hasNext();) {
                children.add(parseNode(it.next()));
            }
            return new AudienceFilterGroup(operator, children);
        }
        if (node.has("field")) {
            AudienceFilterField field = enumOrThrow(AudienceFilterField.class, node.get("field").asString(), "field");
            Map<String, String> params = new LinkedHashMap<>();
            JsonNode paramsNode = node.get("params");
            if (paramsNode != null && paramsNode.isObject()) {
                paramsNode.properties().forEach(entry -> params.put(entry.getKey(), entry.getValue().asString()));
            }
            return new AudienceFilterCondition(field, params);
        }
        throw new InvalidRequestException("Audience filter node must have either operator+children or field");
    }

    private <E extends Enum<E>> E enumOrThrow(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value);
        } catch (Exception e) {
            throw new InvalidRequestException("Unknown audience filter " + fieldName + ": " + value);
        }
    }
}
