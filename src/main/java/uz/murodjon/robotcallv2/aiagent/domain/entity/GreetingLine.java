package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingDirection;

import java.util.Locale;

/**
 * One opening line and the calls it suits: an inbound call opens differently from one the
 * agent placed, and a Russian-speaking caller should not be greeted in Uzbek.
 *
 * @param direction the calls this line is for; null = either way
 * @param language  the call language it is for, as a BCP-47 tag ({@code ru-RU}) or just the
 *                  language ({@code ru}); null = any language
 * @param text      what is said; {@code {{variables}}} are filled from the call's facts
 */
public record GreetingLine(GreetingDirection direction, String language, String text) {

    public GreetingLine {
        language = language == null || language.isBlank() ? null : language.trim();
        text = text == null ? "" : text.trim();
    }

    /** Whether this line may be used on a call in {@code callLanguage} going {@code inbound}. */
    public boolean matches(String callLanguage, boolean inbound) {
        if (direction != null && (direction == GreetingDirection.INBOUND) != inbound) {
            return false;
        }
        return language == null || primaryLanguage(language).equals(primaryLanguage(callLanguage));
    }

    /** How narrowly the line is aimed: a line fixed to both direction and language beats either alone. */
    public int specificity() {
        return (direction != null ? 2 : 0) + (language != null ? 1 : 0);
    }

    private static String primaryLanguage(String tag) {
        if (tag == null) {
            return "";
        }
        String lower = tag.trim().toLowerCase(Locale.ROOT);
        int dash = lower.indexOf('-');
        return dash < 0 ? lower : lower.substring(0, dash);
    }
}
