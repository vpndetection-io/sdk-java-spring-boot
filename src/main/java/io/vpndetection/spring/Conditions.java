package io.vpndetection.spring;

import io.vpndetection.middleware.Bound;
import io.vpndetection.middleware.Condition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A block condition as YAML gave it to us, in the types the core matches against.
 *
 * <p>Spring's binder has no target type for the values inside a {@code Map<String, Object>}, so
 * every scalar arrives as a {@code String}: {@code is_vpn: true} binds to the string "true", and
 * {@code hits: {gte: 5}} to a plain map of strings. A condition matches a served answer by type -
 * a string want only ever matches a string member - so left alone, every condition written in
 * {@code application.yml} silently matches nothing and blocks no one.
 */
final class Conditions {
    private Conditions() {}

    // Deliberately narrower than Double.parseDouble, which also accepts "5d", "0x1p3", "Infinity"
    // and "NaN" - a provider slug that happens to start with a digit must stay a string.
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

    /** The bound condition, with strings read back as the booleans and numbers they were written as. */
    static List<Map<String, Object>> typed(List<Map<String, Object>> conditions) {
        if (conditions == null) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>(conditions.size());
        for (Map<String, Object> one : conditions) {
            out.add(object(one));
        }
        return out;
    }

    private static Map<String, Object> object(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((key, value) -> out.put(key, value(value)));
        return out;
    }

    private static Object value(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return isBound(typed) ? bound(typed) : object(typed);
        }
        if (raw instanceof List<?> anyOf) {
            List<Object> out = new ArrayList<>(anyOf.size());
            for (Object entry : anyOf) {
                out.add(value(entry));
            }
            return out;
        }
        if (raw instanceof String text) {
            return scalar(text);
        }
        return raw;
    }

    // No member the API serves is a boolean or a digits-only string, so reading one back is
    // unambiguous: every remaining string - a provider slug, a confidence, a date - stays a string.
    private static Object scalar(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        return NUMBER.matcher(trimmed).matches() ? (Object) Double.valueOf(trimmed) : text;
    }

    private static boolean isBound(Map<String, Object> raw) {
        return !raw.isEmpty() && Condition.BOUND_KEYS.containsAll(raw.keySet());
    }

    private static Bound bound(Map<String, Object> raw) {
        Bound bound = null;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            double value = number(entry.getValue());
            String side = entry.getKey().toLowerCase(Locale.ROOT);
            bound = switch (side) {
                case "gte" -> bound == null ? Bound.gte(value) : bound.andGte(value);
                case "gt" -> bound == null ? Bound.gt(value) : bound.andGt(value);
                case "lte" -> bound == null ? Bound.lte(value) : bound.andLte(value);
                default -> bound == null ? Bound.lt(value) : bound.andLt(value);
            };
        }
        return bound;
    }

    private static double number(Object raw) {
        if (raw instanceof Number already) {
            return already.doubleValue();
        }
        String text = String.valueOf(raw).trim();
        if (!NUMBER.matcher(text).matches()) {
            throw new IllegalArgumentException(
                    "vpndetection: block condition bound " + raw + " is not a number");
        }
        return Double.parseDouble(text);
    }
}
