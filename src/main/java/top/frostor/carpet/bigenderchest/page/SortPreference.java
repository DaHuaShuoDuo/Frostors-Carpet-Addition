package top.frostor.carpet.bigenderchest.page;

import java.util.Locale;

public record SortPreference(String field, String direction) {
    public static final SortPreference DEFAULT = new SortPreference("name", "ascending");

    public SortPreference {
        field = normalizeField(field);
        direction = normalizeDirection(direction);
    }

    private static String normalizeField(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.equals("amount") ? "amount" : "name";
    }

    private static String normalizeDirection(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.equals("descending") ? "descending" : "ascending";
    }
}
