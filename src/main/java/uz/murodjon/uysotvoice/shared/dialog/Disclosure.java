package uz.murodjon.uysotvoice.shared.dialog;

/**
 * Decides whether a configured §11.1 opening disclosure may actually be spoken.
 *
 * <p>The disclosure is a legal obligation the platform owes the caller, not ordinary
 * content (PROJECT.md §11.1, ROADMAP risk #1: configuration may add to the platform's
 * rules, never remove one). Both places that may word it — a company's own config and a
 * scenario that overrides it — go through here, and a text that fails any check is
 * simply not used: the caller then hears {@link DialogPhrases#disclosure} instead, so
 * the notice is never lost, only worded by the platform.
 *
 * <p>Two ways a text is refused. It has to <b>say both things</b> — that this is an
 * automated system and that the call is recorded — and it has to be in the <b>language
 * the call is being held in</b>, since each of these fields holds one string while a
 * campaign may dial both uz-UZ and ru-RU targets.
 */
public final class Disclosure {

    /** Replaced with the calling company's name, so one shared wording can name each tenant. */
    private static final String COMPANY_PLACEHOLDER = "{company}";

    /** Ways of saying "this is a machine" that count as disclosing it. */
    private static final String[] AUTOMATED = {"avtomatik", "robot", "автоматич", "робот"};

    /** ...and of saying the call is recorded. */
    private static final String[] RECORDED = {"yozib ol", "yozuv", "запис"};

    private Disclosure() {
    }

    /**
     * The configured disclosure to speak, or {@code null} when the platform's own
     * wording should be used instead (nothing configured, wrong language, or a text that
     * does not disclose what it must).
     *
     * @param configured  a company's or a scenario's disclosure text
     * @param language    the call's BCP-47 language
     * @param companyName calling company, substituted for {@code {company}}
     */
    public static String resolve(String configured, String language, String companyName) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String text = configured.trim();
        if (!sameLanguage(text, language) || !discloses(text)) {
            return null;
        }
        return withCompany(text, companyName);
    }

    /**
     * Whether {@code text} states both mandatory facts (§11.1): that the caller is
     * talking to an automated system, and that the call is being recorded.
     *
     * <p>Keyword matching, deliberately. It cannot judge whether a sentence reads well,
     * only whether the two notices are in it at all — which is the part that is not
     * negotiable. Its job is to catch a disclosure replaced by a greeting, not to grade
     * prose.
     */
    public static boolean discloses(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return containsAny(lower, AUTOMATED) && containsAny(lower, RECORDED);
    }

    private static boolean containsAny(String lower, String[] needles) {
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code text} is written in the language the call is being held in. The
     * platform speaks Uzbek and Russian, and those two are told apart by script: a
     * Cyrillic letter anywhere makes it Russian, since Uzbek here is always written in
     * Latin (PROJECT.md §3).
     */
    private static boolean sameLanguage(String text, String language) {
        return isRussianText(text) == (language != null && language.startsWith("ru"));
    }

    private static boolean isRussianText(String text) {
        return text.codePoints().anyMatch(c -> c >= 0x0400 && c <= 0x04FF);
    }

    /**
     * Substitutes the company name. A blank name leaves no double space and no dangling
     * placeholder: the notice that this is a machine and it records stands on its own
     * even when nobody is named (see {@link DialogPhrases#disclosure}).
     */
    private static String withCompany(String text, String companyName) {
        if (!text.contains(COMPANY_PLACEHOLDER)) {
            return text;
        }
        String company = companyName == null ? "" : companyName.trim();
        return text.replace(COMPANY_PLACEHOLDER, company).replaceAll("\\s{2,}", " ").trim();
    }
}
