package uz.murodjon.robotcallv2.company.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.*;
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
 * @param scorecardReviewThreshold   an operator call scored below this (0–100) goes to the
 *                                   review inbox (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.3b)
 * @param telephonyEngine            telephony media engine (ASTERISK, LIVEKIT); the platform's choice,
 *                                   so it is not part of the company's own settings JSON
 */
public record CompanyConfig(
        long id,
        long companyId,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        String timezone,
        int maxAttemptsPerPhonePerWeek,
        int quietDaysAfterConversation,
        boolean respectPromisedDate,
        boolean humanLikePersonaAllowed,
        List<LocalDate> holidays,
        int scorecardReviewThreshold,
        @JsonIgnore TelephonyEngine telephonyEngine,
        Instant createdAt
) {

    public static final int DEFAULT_SCORECARD_REVIEW_THRESHOLD = 60;

    public CompanyConfig {
        holidays = holidays == null ? List.of() : List.copyOf(holidays);
        if (telephonyEngine == null) {
            telephonyEngine = TelephonyEngine.ASTERISK;
        }
    }

    /** Ordinary working hours: a collections call at 07:00 or 22:30 is itself a complaint. */
    private static final LocalTime DEFAULT_DIAL_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_DIAL_WINDOW_END = LocalTime.of(20, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";
    private static final int DEFAULT_MAX_ATTEMPTS_PER_PHONE_PER_WEEK = 3;
    private static final int DEFAULT_QUIET_DAYS_AFTER_CONVERSATION = 1;

    /**
     * What a tenant starts with before anyone edits its settings — the single definition,
     * used both when a company is created and when the default tenant is seeded at startup.
     */
    public static CompanyConfig defaults() {
        return settings(DEFAULT_DIAL_WINDOW_START, DEFAULT_DIAL_WINDOW_END, DEFAULT_TIMEZONE,
                DEFAULT_MAX_ATTEMPTS_PER_PHONE_PER_WEEK, DEFAULT_QUIET_DAYS_AFTER_CONVERSATION, true, false);
    }

    /**
     * The settings alone, for a write. Identity and {@code createdAt} belong to the
     * storage layer, which stamps them itself.
     */
    public static CompanyConfig settings(LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                                         int maxAttemptsPerPhonePerWeek,
                                         int quietDaysAfterConversation, boolean respectPromisedDate,
                                         boolean humanLikePersonaAllowed) {
        return new CompanyConfig(0, 0, dialWindowStart, dialWindowEnd, timezone,
                maxAttemptsPerPhonePerWeek, quietDaysAfterConversation, respectPromisedDate,
                humanLikePersonaAllowed, List.of(), DEFAULT_SCORECARD_REVIEW_THRESHOLD, null, null);
    }

    /**
     * The same, with the days the company does not call at all (NEXT-PLAN C.4) and the score
     * below which an operator call is reviewed, and the telephony engine (null = the default one).
     */
    public static CompanyConfig settings(LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                                         int maxAttemptsPerPhonePerWeek,
                                         int quietDaysAfterConversation, boolean respectPromisedDate,
                                         boolean humanLikePersonaAllowed, List<LocalDate> holidays,
                                         int scorecardReviewThreshold, TelephonyEngine telephonyEngine) {
        return new CompanyConfig(0, 0, dialWindowStart, dialWindowEnd, timezone,
                maxAttemptsPerPhonePerWeek, quietDaysAfterConversation, respectPromisedDate,
                humanLikePersonaAllowed, holidays, scorecardReviewThreshold,
                telephonyEngine, null);
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
