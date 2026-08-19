package com.studen.communication;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Strict allow-list {@code {{token}}} substitution — never a template engine, never arbitrary
 * expression evaluation. Only the 8 spec-listed tokens are ever recognized; anything else
 * (including a typo'd token) is left as literal text rather than throwing, so one bad token can
 * never break a whole campaign's send.
 */
@Component
public class MessageTemplateRenderer {

    public static final java.util.Set<String> ALLOWED_TOKENS = java.util.Set.of(
            "firstName", "lastName", "skillName", "assessmentName", "score", "rank", "challengeName", "roadmapName");

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*}}");

    public String render(String template, Map<String, String> values) {
        if (template == null) {
            return null;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = ALLOWED_TOKENS.contains(token) && values.containsKey(token) ? values.get(token)
                    : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
