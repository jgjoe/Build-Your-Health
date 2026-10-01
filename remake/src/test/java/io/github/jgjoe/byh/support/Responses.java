package io.github.jgjoe.byh.support;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Reads JSON responses as UTF-8. The response charset is not assumed: JSON is UTF-8 by
 * definition and Korean values must survive the round trip.
 */
public final class Responses {

    private Responses() {
    }

    public static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    public static ReadContext json(MvcResult result) {
        return JsonPath.parse(body(result));
    }

    public static String code(MvcResult result) {
        return string(json(result), "$.code");
    }

    public static String message(MvcResult result) {
        return string(json(result), "$.message");
    }

    /** Top-level field names of the response body. */
    @SuppressWarnings("unchecked")
    public static List<String> fieldNames(MvcResult result) {
        Map<String, Object> root = json(result).read("$", Map.class);
        return List.copyOf(root.keySet());
    }

    public static String string(ReadContext json, String path) {
        Object value = json.read(path);
        return value == null ? null : String.valueOf(value);
    }

    public static long longValue(ReadContext json, String path) {
        Object value = json.read(path);
        return ((Number) value).longValue();
    }

    public static List<Long> longs(ReadContext json, String path) {
        List<?> values = json.read(path, List.class);
        return values == null ? List.of() : values.stream().map(value -> ((Number) value).longValue()).toList();
    }

    public static List<String> strings(ReadContext json, String path) {
        List<?> values = json.read(path, List.class);
        return values == null ? List.of() : values.stream().map(value -> Objects.toString(value, null)).toList();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objects(ReadContext json, String path) {
        List<Map<String, Object>> values = json.read(path, List.class);
        return values == null ? List.of() : values;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> nestedObjects(Map<String, Object> object, String field) {
        Object value = object.get(field);
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }
}
