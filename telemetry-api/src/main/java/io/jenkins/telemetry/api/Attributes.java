package io.jenkins.telemetry.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Attributes implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final Attributes EMPTY = new Attributes(Map.of());
    private final Map<String, Object> values;

    private Attributes(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Attributes empty() {
        return EMPTY;
    }

    public static Attributes of(String key, Object value) {
        return builder().put(key, value).build();
    }

    public static Attributes copyOf(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return empty();
        }
        Builder builder = builder();
        values.forEach(builder::put);
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Attributes merge(Attributes other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        Builder builder = builder().putAll(values).putAll(other.values);
        return builder.build();
    }

    @Override
    public String toString() {
        return values.toString();
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("Attribute key must not be blank");
            }
            if (value == null) {
                return this;
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean ||
                  value instanceof String[] || value instanceof long[] || value instanceof double[] ||
                  value instanceof boolean[] || value instanceof Iterable<?>)) {
                value = String.valueOf(value);
            }
            values.put(key, value);
            return this;
        }

        public Builder putAll(Map<String, ?> source) {
            if (source != null) {
                source.forEach(this::put);
            }
            return this;
        }

        public Builder putAll(Attributes source) {
            if (source != null) {
                putAll(source.values);
            }
            return this;
        }

        public Attributes build() {
            return values.isEmpty() ? EMPTY : new Attributes(values);
        }
    }
}
