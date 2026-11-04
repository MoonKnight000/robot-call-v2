package uz.murodjon.robotcallv2.aiagent.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentChatSettingsTest {

    @Test
    void anAgentMayCloseSoonerButNeverLaterThanThePlatform() {
        assertThat(AgentChatSettings.NONE.resolveInactivityTimeoutMinutes(120)).isEqualTo(120);
        assertThat(new AgentChatSettings(15, null, null).resolveInactivityTimeoutMinutes(120)).isEqualTo(15);
        assertThat(new AgentChatSettings(600, null, null).resolveInactivityTimeoutMinutes(120)).isEqualTo(120);
        assertThat(new AgentChatSettings(null, 20, null).resolveMaxMessages(40)).isEqualTo(20);
        assertThat(new AgentChatSettings(null, 100, null).resolveMaxMessages(40)).isEqualTo(40);
    }
}
