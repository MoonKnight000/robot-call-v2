package uz.murodjon.robotcallv2.knowledgebase.application.dto;

import java.time.Instant;

/**
 * A question callers asked that the knowledge base had nothing for — the next curated answer or document to add.
 *
 * @param question          the latest wording it was asked in (phone numbers and the like masked, as in the trace)
 * @param count             how many calls or chats asked it, by its normalised wording
 * @param lastCallAttemptId the latest call that asked it, to open its trace; 0 when it was a chat
 * @param lastChatSessionId the latest chat that asked it; 0 when it was a call
 */
public record KnowledgeGapRow(
        String question,
        int count,
        Instant lastAskedAt,
        long lastCallAttemptId,
        long lastChatSessionId
) {
}
