package com.devflow.generate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small template language.
 *
 * <pre>
 *   {{entity}}                 the value exactly as typed
 *   {{entity|pascal}}          OrderItem
 *   {{entity|plural|kebab}}    order-items   (filters apply left to right)
 *   {{today}} {{year}}         built-in values
 * </pre>
 *
 * Anything that does not look like {{simpleName}} is left untouched, so GitHub Actions
 * expressions such as ${{ secrets.TOKEN }} pass through unchanged.
 */
public final class TemplateEngine {

    static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*((?:\\|\\s*[A-Za-z]+\\s*)*)}}");

    public static final Map<String, UnaryOperator<String>> FILTERS;

    static {
        Map<String, UnaryOperator<String>> f = new LinkedHashMap<>();
        f.put("pascal", CaseConverter::pascal);
        f.put("camel", CaseConverter::camel);
        f.put("kebab", CaseConverter::kebab);
        f.put("snake", CaseConverter::snake);
        f.put("constant", CaseConverter::constant);
        f.put("title", CaseConverter::title);
        f.put("plural", CaseConverter::plural);
        f.put("path", CaseConverter::path);
        f.put("lower", s -> s.toLowerCase(java.util.Locale.ROOT));
        f.put("upper", s -> s.toUpperCase(java.util.Locale.ROOT));
        FILTERS = Map.copyOf(f);
    }

    public static final Set<String> BUILT_INS = Set.of("today", "year");

    private TemplateEngine() {
    }

    /** Names of the variables a user must fill in, in order of first appearance. */
    public static Set<String> findVariables(Iterable<String> templates) {
        Set<String> names = new LinkedHashSet<>();
        for (String template : templates) {
            Matcher m = PLACEHOLDER.matcher(template == null ? "" : template);
            while (m.find()) {
                if (!BUILT_INS.contains(m.group(1))) {
                    names.add(m.group(1));
                }
            }
        }
        return names;
    }

    /** Returns problems such as unknown filters; empty list means the template is valid. */
    public static List<String> validate(String template) {
        List<String> problems = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template == null ? "" : template);
        while (m.find()) {
            for (String filter : parseFilters(m.group(2))) {
                if (!FILTERS.containsKey(filter)) {
                    problems.add("Unknown filter '" + filter + "' in " + m.group()
                            + ". Available: " + String.join(", ", FILTERS.keySet().stream().sorted().toList()));
                }
            }
        }
        return problems;
    }

    public static String render(String template, Map<String, String> values) {
        Map<String, String> all = new LinkedHashMap<>(values);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        all.putIfAbsent("today", today.toString());
        all.putIfAbsent("year", String.valueOf(today.getYear()));

        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = all.get(m.group(1));
            if (value == null) {
                throw new IllegalArgumentException("No value for variable '" + m.group(1) + "'");
            }
            for (String filter : parseFilters(m.group(2))) {
                UnaryOperator<String> fn = FILTERS.get(filter);
                if (fn == null) {
                    throw new IllegalArgumentException("Unknown filter '" + filter + "'");
                }
                value = fn.apply(value);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static List<String> parseFilters(String group) {
        List<String> filters = new ArrayList<>();
        if (group == null || group.isBlank()) {
            return filters;
        }
        for (String part : group.split("\\|")) {
            if (!part.isBlank()) {
                filters.add(part.strip());
            }
        }
        return filters;
    }
}
