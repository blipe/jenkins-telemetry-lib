package io.jenkins.telemetry.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves collector configuration placeholders without persisting resolved secrets. */
public final class ConfigurationResolver {
    private static final Pattern PLACEHOLDER =
        Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_-]*):([^}]+)}");
    private static final Map<String, Function<String, String>> SOURCES = new ConcurrentHashMap<>();

    static {
        SOURCES.put("env", System::getenv);
        SOURCES.put("sys", System::getProperty);
    }

    private ConfigurationResolver() {
    }

    public static void registerSource(String source, Function<String, String> resolver) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source is required");
        if (resolver == null) throw new IllegalArgumentException("resolver is required");
        SOURCES.put(source.trim(), resolver);
    }

    public static String resolve(String value) {
        if (value == null || value.indexOf('$') < 0) return value;
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String source = matcher.group(1);
            String key = matcher.group(2);
            Function<String, String> resolver = SOURCES.get(source);
            if (resolver == null) {
                throw new IllegalArgumentException("Unknown configuration placeholder source: " + source);
            }
            String replacement = resolver.apply(key);
            if (replacement == null) {
                throw new IllegalArgumentException("Unresolved configuration placeholder: " + matcher.group());
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static Map<String, String> resolve(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> result.put(key, resolve(value)));
        }
        return result;
    }
}
