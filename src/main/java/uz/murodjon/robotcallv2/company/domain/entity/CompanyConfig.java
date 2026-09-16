package uz.murodjon.robotcallv2.company.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Domain record for company operational settings (ROADMAP B.1/B.3).
 *
 * @param maxAttemptsPerPhonePerWeek placed outbound attempts one number may receive in any
 *                                   seven days, across all of this company's campaigns;
 *                                   0 = no cap
 * @param quietDaysAfterConversation days after a real conversation with a number before
 *                                   any campaign calls it again; 0 = no quiet period. A
 *                                   conversation that ended in a requested callback
 *                                   ({@code CALLBACK_REQUESTED}) does not start one
 * @param respectPromisedDate        no outbound call to a number while a promise to pay it
 *                                   made is still within its promised date
 * @param humanLikePersonaAllowed    whether this company's agents may present as a person
 *                                   ({@code AgentPersona.HUMAN_LIKE}) and so skip the §11.1
 *                                   automated-call disclosure. When false such an agent
 *                                   cannot be saved, and one saved earlier speaks as an
 *                                   AI assistant, disclosure included
 */
public record CompanyConfig(
        long id,
        long companyId,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        String timezone,
        Language defaultLanguage,
        List<Language> supportedLanguages,
        String disclosureText,
        int maxAttemptsPerPhonePerWeek,
        int quietDaysAfterConversation,
        boolean respectPromisedDate,
        boolean humanLikePersonaAllowed,
        List<LocalDate> holidays,
        Instant createdAt
) {

    public CompanyConfig {
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
    }

    /** Ordinary working hours: a collections call at 07:00 or 22:30 is itself a complaint. */
    private static final LocalTime DEFAULT_DIAL_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_DIAL_WINDOW_END = LocalTime.of(20, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";
    private static final Language DEFAULT_LANGUAGE = Language.UZ_UZ;
    private static final List<Language> DEFAULT_SUPPORTED_LANGUAGES =
            List.of(Language.UZ_UZ, Language.RU_RU);
    private static final int DEFAULT_MAX_ATTEMPTS_PER_PHONE_PER_WEEK = 3;
    private static final int DEFAULT_QUIET_DAYS_AFTER_CONVERSATION = 1;
    private static final String DEFAULT_DISCLOSURE_TEXT =
            "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.";

    /**
     * What a tenant starts with before anyone edits its settings — the single definition,
     * used both when a company is created and when the default tenant is seeded at startup.
     * A 09:00–20:00 window; no call while a promise to pay is still in its term — calling
     * someone who has just agreed a date is the complaint, whatever the numbers; at most
     * {@value #DEFAULT_MAX_ATTEMPTS_PER_PHONE_PER_WEEK} placed calls to a number a week and
     * a day of quiet after a real conversation, which the company may loosen; and no
     * human-like persona until a platform admin allows one.
     */
    public static CompanyConfig defaults() {
        return settings(DEFAULT_DIAL_WINDOW_START, DEFAULT_DIAL_WINDOW_END, DEFAULT_TIMEZONE,
                DEFAULT_LANGUAGE, DEFAULT_SUPPORTED_LANGUAGES, DEFAULT_DISCLOSURE_TEXT,
                DEFAULT_MAX_ATTEMPTS_PER_PHONE_PER_WEEK, DEFAULT_QUIET_DAYS_AFTER_CONVERSATION, true, false);
    }

    /**
     * The settings alone, for a write. Identity and {@code createdAt} belong to the
     * storage layer, which stamps them itself.
     */
    public static CompanyConfig settings(LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                                         Language defaultLanguage, List<Language> supportedLanguages,
                                         String disclosureText, int maxAttemptsPerPhonePerWeek,
                                         int quietDaysAfterConversation, boolean respectPromisedDate,
                                         boolean humanLikePersonaAllowed) {
        return new CompanyConfig(0, 0, dialWindowStart, dialWindowEnd, timezone,
                defaultLanguage, supportedLanguages, disclosureText,
                maxAttemptsPerPhonePerWeek, quietDaysAfterConversation, respectPromisedDate,
                humanLikePersonaAllowed, List.of(), null);
    }

    /** The same, with the days the company does not call at all (NEXT-PLAN C.4). */
    public static CompanyConfig settings(LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                                         Language defaultLanguage, List<Language> supportedLanguages,
                                         String disclosureText, int maxAttemptsPerPhonePerWeek,
                                         int quietDaysAfterConversation, boolean respectPromisedDate,
                                         boolean humanLikePersonaAllowed, List<LocalDate> holidays) {
        return new CompanyConfig(0, 0, dialWindowStart, dialWindowEnd, timezone,
                defaultLanguage, supportedLanguages, disclosureText,
                maxAttemptsPerPhonePerWeek, quietDaysAfterConversation, respectPromisedDate,
                humanLikePersonaAllowed, holidays, null);
    }

    /** Whether any contact-frequency rule is switched on, so the dialer has history to read. */
    public boolean limitsContactFrequency() {
        return maxAttemptsPerPhonePerWeek > 0 || quietDaysAfterConversation > 0 || respectPromisedDate;
    }

    /** Whether {@code day}, on the company's own calendar, is one it does not call on. */
    public boolean isHoliday(LocalDate day) {
        return holidays.contains(day);
    }

    /**
     * The company's clock — what its dial window, callbacks and recurring campaigns are
     * read against. A blank or unknown zone id falls back to the platform default rather
     * than failing a dial tick.
     */
    public ZoneId zoneId() {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }
}
