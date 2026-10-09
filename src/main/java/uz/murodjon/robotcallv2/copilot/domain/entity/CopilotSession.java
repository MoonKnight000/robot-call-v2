package uz.murodjon.robotcallv2.copilot.domain.entity;

import java.time.Instant;

/**
 * One conversation with the copilot, owned by the user who started it.
 *
 * @param title the first question, cut short
 */
public record CopilotSession(
        long id,
        long companyId,
        long userId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {

    public static final int TITLE_LENGTH = 200;

    /** A new conversation; identity and times are the storage layer's. */
    public static CopilotSession started(long userId, String firstQuestion) {
        String title = firstQuestion.strip();
        return new CopilotSession(0, 0, userId,
                title.length() > TITLE_LENGTH ? title.substring(0, TITLE_LENGTH - 1) + "…" : title, null, null);
    }
}
