package uz.murodjon.robotcallv2.aiagent.domain.entity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentSpeechLanguagesTest {

    @Test
    void anAgentWithoutSpeechLanguagesSpeaksItsOwnLanguageOnly() {
        AiAgent agent = AiAgent.builder().name("Agent").language("uz-UZ").build();

        assertThat(agent.speechLanguages()).containsExactly("uz-UZ");
        assertThat(agent.findSpokenLanguages()).containsExactly("uz-UZ");
        assertThat(agent.multilingual()).isFalse();
    }

    @Test
    void theAgentsOwnLanguageLeadsAndDuplicatesAndBlanksAreDropped() {
        AiAgent agent = AiAgent.builder().name("Agent").language("uz-UZ")
                .speechLanguages(List.of("ru-RU", "uz-UZ", " ", "ru-RU")).build();

        assertThat(agent.speechLanguages()).containsExactly("uz-UZ", "ru-RU");
        assertThat(agent.multilingual()).isTrue();
    }

    @Test
    void aLanguageWithAVoiceIsSpokenEvenWhenTheListDoesNotNameIt() {
        AiAgentVoice voice = new AiAgentVoice("nigora", 1.0, Map.of("ru-RU", "alena"), null, true);
        AiAgent agent = AiAgent.builder().name("Agent").language("uz-UZ").voice(voice).build();

        assertThat(agent.findSpokenLanguages()).containsExactly("uz-UZ", "ru-RU");
        assertThat(agent.multilingual()).isTrue();
    }
}
