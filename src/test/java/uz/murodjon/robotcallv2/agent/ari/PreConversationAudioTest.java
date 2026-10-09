package uz.murodjon.robotcallv2.agent.ari;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PreConversationAudioTest {

    private final FlowPrompter prompter = mock(FlowPrompter.class);
    private final CallMediaFactory callMediaFactory = mock(CallMediaFactory.class);
    private final RtpEndpoint endpoint = mock(RtpEndpoint.class);
    private final PreConversationAudio audio = new PreConversationAudio(provider(prompter), callMediaFactory);

    @Test
    void playsTheAgentsFileWithTheCallsCompany() {
        short[] pcm = new short[8000];
        when(prompter.recording(7L, 42L)).thenReturn(pcm);

        audio.play("chan-1", 7L, agentWithAudio(42L), endpoint);

        verify(endpoint).playPcm(pcm);
        verify(callMediaFactory).awaitSilence(eq(endpoint), anyLong());
    }

    @Test
    void anAgentWithoutAudioPlaysNothing() {
        audio.play("chan-1", 7L, agentWithAudio(null), endpoint);

        verifyNoInteractions(prompter, endpoint);
    }

    @Test
    void anUnreadableFileIsSkipped() {
        when(prompter.recording(7L, 42L)).thenReturn(new short[0]);

        audio.play("chan-1", 7L, agentWithAudio(42L), endpoint);

        verify(endpoint, never()).playPcm(any());
    }

    private static AiAgent agentWithAudio(Long fileId) {
        AiAgent agent = AiAgentFixtures.agent(1L, 7L, 3L, "uz-UZ", null);
        AiAgentCallBehaviour b = agent.callBehaviour();
        AiAgentCallBehaviour withAudio = new AiAgentCallBehaviour(b.interruptionSensitivity(), b.endpointingDelayMs(),
                b.dtmfInputEnabled(), b.voicemailAction(), b.voicemailMessage(), b.midCallSmsEnabled(),
                b.midCallSmsTemplate(), b.transferPhoneNumber(), b.transferMessage(), b.transferQueueId(), fileId);
        return agent.withAdvanced(agent.ambience(), withAudio, agent.dataPolicy(), agent.voice(),
                agent.preemptiveGeneration(), agent.ivrNavigationEnabled(), agent.useRag(),
                agent.initiationWebhook(), agent.postCallWebhook());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<FlowPrompter> provider(FlowPrompter prompter) {
        ObjectProvider<FlowPrompter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(prompter);
        return provider;
    }
}
