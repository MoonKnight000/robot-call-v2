package uz.murodjon.robotcallv2.aiagent.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentChannelTest {

    @Test
    void aChatAgentTakesNoCallsAndAVoiceAgentNoChats() {
        assertThat(AiAgentChannel.VOICE.takesCalls()).isTrue();
        assertThat(AiAgentChannel.VOICE.takesChats()).isFalse();
        assertThat(AiAgentChannel.CHAT.takesCalls()).isFalse();
        assertThat(AiAgentChannel.CHAT.takesChats()).isTrue();
        assertThat(AiAgentChannel.OMNICHANNEL.takesCalls()).isTrue();
        assertThat(AiAgentChannel.OMNICHANNEL.takesChats()).isTrue();
    }
}
