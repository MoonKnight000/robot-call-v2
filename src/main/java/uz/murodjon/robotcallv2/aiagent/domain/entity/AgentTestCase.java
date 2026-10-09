package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One scripted caller an agent is evaluated against (NEXT-PLAN A.2).
 *
 * @param personaPrompt     instructions to the model that plays the caller
 * @param facts             the call's facts, as the CRM would supply them
 * @param language          the caller's language; null uses the agent's
 * @param expectDisposition how the call must end, or null to not judge it
 * @param expectStage       the stage it must end in, or null
 * @param mustSay           phrases the agent has to say at some point (case-insensitive)
 * @param mustNotSay        phrases the agent must never say — "chegirma", a figure, a promise
 * @param maxTurns          turns before the simulated call is cut off
 * @param toolMocks         what each of the agent's company tools answers in a simulation, by tool name;
 *                          a tool not listed answers that it is unavailable. No request ever leaves.
 * @param expectTools       tools the agent has to call at some point
 */
public record AgentTestCase(
        Long id,
        long companyId,
        long agentId,
        String name,
        String personaPrompt,
        Map<String, Object> facts,
        String language,
        String expectDisposition,
        String expectStage,
        List<String> mustSay,
        List<String> mustNotSay,
        int maxTurns,
        Instant createdAt,
        Map<String, String> toolMocks,
        List<String> expectTools
) {

    public static final int DEFAULT_MAX_TURNS = 12;

    public AgentTestCase {
        facts = facts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        mustSay = mustSay == null ? List.of() : List.copyOf(mustSay);
        mustNotSay = mustNotSay == null ? List.of() : List.copyOf(mustNotSay);
        maxTurns = maxTurns > 0 ? maxTurns : DEFAULT_MAX_TURNS;
        toolMocks = toolMocks == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(toolMocks));
        expectTools = expectTools == null ? List.of() : List.copyOf(expectTools);
    }
}
