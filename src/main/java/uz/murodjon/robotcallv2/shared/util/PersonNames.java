package uz.murodjon.robotcallv2.shared.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

    /** A surname ("Jo'rayev", "Karimova", "Petrovich") — by its ending; a CRM may put it before or after the first name. */
    private static final Pattern SURNAME = Pattern.compile(
            "(?iu).+(ov|ev|yev|ova|eva|yeva|ovich|evich|ovna|evna|skiy|skaya|ов|ев|ова|ева|ович|евич|овна|евна|ский|ская)");
    /** A male surname ending: the Uzbek address is "aka". */
    private static final Pattern MALE_SURNAME = Pattern.compile("(?iu).+(ov|ev|yev|ovich|evich|skiy|ов|ев|ович|евич|ский)");
    /** A female surname ending: the Uzbek address is "opa". */
    private static final Pattern FEMALE_SURNAME = Pattern.compile("(?iu).+(ova|eva|yeva|ovna|evna|skaya|ова|ева|овна|евна|ская)");
    /** The patronymic markers that follow a father's name ("Anvar Karim o'g'li"). */
    private static final Set<String> PATRONYMIC_WORDS = Set.of("o'g'li", "og'li", "ogli", "ugli", "qizi", "kizi");

    private PersonNames() {
    }

    /**
     * The first name alone — what a person says to the caller ("Anvar" from "Jo'rayev Anvar
     * Karim o'g'li"). A recorded call opened with "Assalomu alaykum, Anvar Jo'rayev!", which
     * no live operator says. The name as given when nothing in it looks like a first name.
     */
    public static String findFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return fullName;
        }
        String[] words = fullName.strip().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            boolean fathersName = i + 1 < words.length && isPatronymicWord(words[i + 1]);
            if (!fathersName && !isPatronymicWord(word) && !SURNAME.matcher(word).matches()) {
                return word;
            }
        }
        return words[0];
    }

    private static boolean isPatronymicWord(String word) {
        return PATRONYMIC_WORDS.contains(word.toLowerCase(Locale.ROOT).replace('’', '\'').replace('ʻ', '\''));
    }

    /**
     * How an Uzbek speaker addresses the person: "Anvar aka", "Dilnoza opa" when the surname
     * tells the gender, the first name alone when it does not.
     */
    public static String findUzbekAddress(String fullName) {
        String firstName = findFirstName(fullName);
        if (firstName == null || firstName.isBlank()) {
            return firstName;
        }
        for (String word : fullName.strip().split("\\s+")) {
            if (FEMALE_SURNAME.matcher(word).matches()) {
                return firstName + " opa";
            }
            if (MALE_SURNAME.matcher(word).matches()) {
                return firstName + " aka";
            }
        }
        return firstName;
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
