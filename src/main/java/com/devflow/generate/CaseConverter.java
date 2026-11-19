package com.devflow.generate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Converts free text such as "order item", "OrderItem" or "order_item" between the
 * naming styles developers type by hand all day (PascalCase, camelCase, kebab-case, ...).
 */
public final class CaseConverter {

    private CaseConverter() {
    }

    /** Splits text into lower-case words: "OrderItem v2-api" -> [order, item, v2, api]. */
    public static List<String> words(String text) {
        String spaced = text
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")      // orderItem -> order Item
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");   // HTTPServer -> HTTP Server
        return Arrays.stream(spaced.split("[^A-Za-z0-9]+"))
                .filter(w -> !w.isEmpty())
                .map(w -> w.toLowerCase(Locale.ROOT))
                .toList();
    }

    public static String pascal(String text) {
        return words(text).stream().map(CaseConverter::capitalize).collect(Collectors.joining());
    }

    public static String camel(String text) {
        String pascal = pascal(text);
        return pascal.isEmpty() ? pascal : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    public static String kebab(String text) {
        return String.join("-", words(text));
    }

    public static String snake(String text) {
        return String.join("_", words(text));
    }

    public static String constant(String text) {
        return snake(text).toUpperCase(Locale.ROOT);
    }

    public static String title(String text) {
        return words(text).stream().map(CaseConverter::capitalize).collect(Collectors.joining(" "));
    }

    /** Simple English plural of the last word: item -> items, category -> categories, box -> boxes. */
    public static String plural(String text) {
        String t = text.strip();
        if (t.isEmpty()) {
            return t;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.endsWith("y") && t.length() > 1 && "aeiou".indexOf(lower.charAt(lower.length() - 2)) < 0) {
            return t.substring(0, t.length() - 1) + (Character.isUpperCase(t.charAt(t.length() - 1)) ? "IES" : "ies");
        }
        boolean upper = Character.isUpperCase(t.charAt(t.length() - 1));
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("ch") || lower.endsWith("sh")) {
            return t + (upper ? "ES" : "es");
        }
        return t + (upper ? "S" : "s");
    }

    /** Java package to folder path: com.acme.shop -> com/acme/shop */
    public static String path(String text) {
        return text.strip().replace('.', '/');
    }

    private static String capitalize(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
