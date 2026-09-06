package uz.murodjon.robotcallv2.memory.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/**
 * One past conversation with a client, as the agent will hear about it next time:
 * when it was, which scenario ran, how it ended and what the summary LLM made of it.
 */
public record RememberedCall(
        Instant at,
        String scenarioKey,
        Disposition disposition,
        String summary
) {
}
