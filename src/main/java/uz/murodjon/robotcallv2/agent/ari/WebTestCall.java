package uz.murodjon.robotcallv2.agent.ari;

import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.time.Instant;

/**
 * A browser test call waiting for the browser to dial in ({@code POST /api/calls/web-test}
 * handed out the session id; the dialplan passes it back as a Stasis argument). Exactly
 * one source is set: a campaign profile, an unsaved scenario draft, or a saved scenario id
 * ({@code null} meaning the configured default test scenario).
 */
public record WebTestCall(
        OutboundCall outbound,
        ScenarioDefinition definition,
        Long scenarioId,
        Instant createdAt
) {
}
