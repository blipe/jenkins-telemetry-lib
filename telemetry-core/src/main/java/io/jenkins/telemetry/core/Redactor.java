package io.jenkins.telemetry.core;

import io.jenkins.telemetry.api.Attributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class Redactor {
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
        "(?i).*(?:authorization|password|passwd|pwd|token|secret|api[_-]?key).*");

    private final List<Rule> rules;

    private Redactor(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    static Redactor withDefaults(Collection<String> expressions, String replacement) {
        List<Rule> rules = new ArrayList<>();
        rules.add(new Rule(Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s]+"), "$1***"));
        rules.add(new Rule(Pattern.compile(
            "(?i)((?:password|passwd|pwd|token|secret|api[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"),
            "$1***"));
        rules.add(new Rule(Pattern.compile(
            "(?i)(https?://[^\\s:/]+:)[^@\\s]+(@)"), "$1***$2"));
        rules.add(new Rule(Pattern.compile(
            "(?i)([?&](?:password|passwd|pwd|token|secret|api[_-]?key)=)[^&#\\s]+"), "$1***"));

        String customReplacement = replacement == null ? "***" : replacement;
        if (expressions != null) {
            for (String expression : expressions) {
                if (expression != null && !expression.isBlank()) {
                    rules.add(new Rule(Pattern.compile(expression), customReplacement));
                }
            }
        }
        return new Redactor(rules);
    }

    String redact(String value) {
        if (value == null || rules.isEmpty()) return value;
        String result = value;
        for (Rule rule : rules) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    Attributes redact(Attributes attributes) {
        if (attributes == null || attributes.isEmpty()) return Attributes.empty();
        return Attributes.copyOf(redact(attributes.asMap()));
    }

    Map<String, Object> redact(Map<String, ?> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key,
            SENSITIVE_KEY.matcher(key).matches() ? "***" : redactValue(value)));
        return result;
    }

    private Object redactValue(Object value) {
        if (value instanceof String string) return redact(string);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                result.put(name, SENSITIVE_KEY.matcher(name).matches() ? "***" : redactValue(item));
            });
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(item -> result.add(redactValue(item)));
            return result;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(redactValue(java.lang.reflect.Array.get(value, i)));
            }
            return result;
        }
        return value;
    }

    private record Rule(Pattern pattern, String replacement) {
    }
}
