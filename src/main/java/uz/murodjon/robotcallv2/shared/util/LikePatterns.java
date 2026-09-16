package uz.murodjon.robotcallv2.shared.util;

/**
 * Turns a user's search text into a LIKE "contains" pattern that matches it literally.
 *
 * <p>Unescaped, a {@code %} or {@code _} typed into a search box acted as a wildcard —
 * "{@code _}" matched any one character. Every query that takes one of these patterns must
 * say {@code ESCAPE '!'} (SQL and JPQL) or pass {@link #ESCAPE} to
 * {@code CriteriaBuilder.like}.
 *
 * <p>The escape character is {@code !} rather than the usual backslash: a backslash means
 * something different in a Java literal, an HQL string literal (Hibernate 6 reads escape
 * sequences there) and a PostgreSQL literal, and a character that is special in none of
 * them is much harder to get wrong.
 */
public final class LikePatterns {

    public static final char ESCAPE = '!';

    private LikePatterns() {
    }

    /**
     * {@code %text%} with the text's own wildcards escaped, or {@code null} for a blank
     * search — the queries read null as "do not narrow". Trims; case is the caller's.
     */
    public static String buildContainsPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + escape(search.trim()) + "%";
    }

    /** {@code text} with {@code %}, {@code _} and the escape character itself escaped. */
    public static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ESCAPE || ch == '%' || ch == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }
}
