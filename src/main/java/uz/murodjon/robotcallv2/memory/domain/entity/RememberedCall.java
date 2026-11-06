package uz.murodjon.robotcallv2.memory.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Objects;

/**
 * One past conversation with a client, as the agent will hear about it next time:
 * when it was, which scenario ran, how it ended and what the summary LLM made of it.
 *
 * @param id            stable handle an operator edits or deletes the entry by. An entry saved before ids
 *                      existed gets one derived from its content, so it stays the same across reads
 * @param callAttemptId the call it summarises; {@code null} for a chat or an entry saved before this was kept
 * @param chatSessionId the chat it summarises; {@code null} for a call
 */
public record RememberedCall(
        String id,
        Instant at,
        String scenarioKey,
        Disposition disposition,
        String summary,
        Long callAttemptId,
        Long chatSessionId
) {

    public RememberedCall {
        if (id == null || id.isBlank()) {
            id = "c" + Integer.toHexString(Objects.hash(at, scenarioKey, disposition, summary));
        }
    }

    /** A call just summarised; its id is given by the memory service when it is stored. */
    public static RememberedCall ofCall(long callAttemptId, Instant at, String scenarioKey, Disposition disposition,
                                        String summary) {
        return new RememberedCall(null, at, scenarioKey, disposition, summary, callAttemptId, null);
    }

    /** A chat conversation just summarised. */
    public static RememberedCall ofChat(long chatSessionId, Instant at, String scenarioKey, Disposition disposition,
                                        String summary) {
        return new RememberedCall(null, at, scenarioKey, disposition, summary, null, chatSessionId);
    }

    public RememberedCall withId(String id) {
        return new RememberedCall(id, at, scenarioKey, disposition, summary, callAttemptId, chatSessionId);
    }

    public RememberedCall withSummary(String summary) {
        return new RememberedCall(id, at, scenarioKey, disposition, summary, callAttemptId, chatSessionId);
    }
}
