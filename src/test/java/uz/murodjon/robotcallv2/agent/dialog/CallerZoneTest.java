package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CallerZoneTest {

    @Test
    void anAgentWithoutAZoneRunsOnThePlatformZone() {
        assertThat(SystemPromptFactory.findCallerZone(AiAgent.builder().name("Agent").build()))
                .isEqualTo(SystemPromptFactory.CALLER_ZONE);
        assertThat(SystemPromptFactory.findCallerZone(null)).isEqualTo(SystemPromptFactory.CALLER_ZONE);
    }

    @Test
    void anAgentZoneIsUsed() {
        AiAgent agent = AiAgent.builder().name("Agent").timezone("Europe/Moscow").build();

        assertThat(SystemPromptFactory.findCallerZone(agent)).isEqualTo(ZoneId.of("Europe/Moscow"));
    }

    @Test
    void aBlankZoneMeansNotSet() {
        assertThat(AiAgent.builder().name("Agent").timezone("  ").build().timezone()).isNull();
    }
}
