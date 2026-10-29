package uz.murodjon.robotcallv2.aiagent.domain.entity;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWebhookConfigTest {

    @Test
    void legacyConfigurationDefaultsToNoCallTimeEvents() {
        AgentWebhookConfig config = new AgentWebhookConfig("https://example.com", "POST", null, null, null, null);

        assertEquals(Set.of(), config.events());
    }

    @Test
    void selectedEventsAreImmutable() {
        AgentWebhookConfig config = new AgentWebhookConfig("https://example.com", "POST", null, null, null, null,
                Set.of(AgentWebhookEvent.STAGE_TRANSITION));

        assertThrows(UnsupportedOperationException.class,
                () -> config.events().add(AgentWebhookEvent.TOOL_EXECUTED));
    }
}
