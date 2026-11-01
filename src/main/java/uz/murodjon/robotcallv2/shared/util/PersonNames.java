package uz.murodjon.robotcallv2.shared.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a name from a CRM is a person's name the agent can say. A CRM fills the name of a lead
 * it created by itself with a placeholder ("Пропущенный звонок +998…", "Lead #42"), and the
 * agent greeted the caller with it.
 */
public final class PersonNames {

    /** Whole words a CRM uses in place of a name, compared case-insensitively ("Лидия" is a name, "лид" is not). */
    private static final Set<String> PLACEHOLDER_WORDS = Set.of(
            "звонок", "звонка", "пропущенный", "входящий", "исходящий", "заявка", "лид", "клиент", "имени", "неизвестный",
            "qo'ng'iroq", "qongiroq", "ariza", "mijoz", "noma'lum", "nomalum",
            "call", "missed", "lead", "client", "customer", "unknown", "name");

    private PersonNames() {
    }

    /** False for a blank name, one with a digit in it (a phone or a number), or a CRM placeholder. */
    public static boolean isUsable(String name) {
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        return Arrays.stream(name.toLowerCase(Locale.ROOT).replace('’', '\'').split("[^\\p{L}']+"))
                .noneMatch(PLACEHOLDER_WORDS::contains);
    }
}
