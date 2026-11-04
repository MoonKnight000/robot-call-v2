package uz.murodjon.robotcallv2.agent.dialog;

import java.time.Instant;

/**
 * One call still in conversation, as {@link DialogEngine} sees it — no campaign/phone
 * context, since the engine only knows the dialog side (§10.2/§10.3 UI-DESIGN.md "Jonli
 * qo'ng'iroqlar"). {@code ActiveCallService} joins this with {@code OutboundCallRegistry} to
 * add the campaign and phone number for the API response.
 *
 * @param dialogState FSM state name (e.g. {@code DEBT_NOTICE})
 * @param clientName  debtor name from the call's facts, or null (manual/test call)
 * @param inbound     the caller rang in, rather than being dialled
 * @param agentId     the AI agent speaking on the call, or null (no agent: legacy scenario call)
 * @param agentName   that agent's name, or null
 */
public record LiveDialogSnapshot(
        String channelId,
        Instant startedAt,
        String dialogState,
        String language,
        String clientName,
        boolean inbound,
        Long agentId,
        String agentName
) {
}
