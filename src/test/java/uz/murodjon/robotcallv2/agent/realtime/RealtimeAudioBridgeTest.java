package uz.murodjon.robotcallv2.agent.realtime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RealtimeAudioBridgeTest {

    @Test
    void zeroesEchoFramesWhenBotLevelIsHigh() {
        RealtimeSession session = mock(RealtimeSession.class);
        RealtimeAudioBridge bridge = new RealtimeAudioBridge();
        // botLevel = 2000, echoGateRatio = 0.25 -> echo threshold = 500, minBotLevel = 300
        bridge.arm(session, 8000, () -> 2000.0, 0.25, 300.0);
        bridge.open();

        // Echo frame: amplitude 300 (RMS ~300 < 500 threshold)
        short[] echoFrame = new short[160];
        Arrays.fill(echoFrame, (short) 300);

        bridge.onAudio(echoFrame, echoFrame.length);

        ArgumentCaptor<short[]> captor = ArgumentCaptor.forClass(short[].class);
        verify(session).sendAudio(captor.capture());
        short[] sent = captor.getValue();

        // Must be zeroed out
        for (short sample : sent) {
            assertThat(sample).isZero();
        }
        assertThat(bridge.echoSuppressedSeconds()).isGreaterThan(0.0);
    }

    @Test
    void allowsCallerBargeInWhenLouderThanEcho() {
        RealtimeSession session = mock(RealtimeSession.class);
        RealtimeAudioBridge bridge = new RealtimeAudioBridge();
        // botLevel = 2000, echoGateRatio = 0.25 -> threshold = 500
        bridge.arm(session, 8000, () -> 2000.0, 0.25, 300.0);
        bridge.open();

        // Loud caller speech: amplitude 2000 (RMS ~2000 > 500)
        short[] callerFrame = new short[160];
        Arrays.fill(callerFrame, (short) 2000);

        bridge.onAudio(callerFrame, callerFrame.length);

        ArgumentCaptor<short[]> captor = ArgumentCaptor.forClass(short[].class);
        verify(session).sendAudio(captor.capture());
        short[] sent = captor.getValue();

        assertThat(sent[0]).isEqualTo((short) 2000);
        assertThat(bridge.echoSuppressedSeconds()).isZero();
    }

    @Test
    void zeroesLowEnergyNoiseFloorDuringSilence() {
        RealtimeSession session = mock(RealtimeSession.class);
        RealtimeAudioBridge bridge = new RealtimeAudioBridge();
        // Bot is not talking: botLevel = 0.0
        bridge.arm(session, 8000, () -> 0.0, 0.25, 300.0);
        bridge.open();

        // Low energy microphone noise: amplitude 30 (RMS ~30 < 70 noise floor)
        short[] noiseFrame = new short[160];
        Arrays.fill(noiseFrame, (short) 30);

        bridge.onAudio(noiseFrame, noiseFrame.length);

        ArgumentCaptor<short[]> captor = ArgumentCaptor.forClass(short[].class);
        verify(session).sendAudio(captor.capture());
        short[] sent = captor.getValue();

        for (short sample : sent) {
            assertThat(sample).isZero();
        }
    }

    @Test
    void allowsNormalCallerSpeechWhenBotIsSilent() {
        RealtimeSession session = mock(RealtimeSession.class);
        RealtimeAudioBridge bridge = new RealtimeAudioBridge();
        bridge.arm(session, 8000, () -> 0.0, 0.25, 300.0);
        bridge.open();

        // Normal caller speech: amplitude 500 (RMS ~500 > 70)
        short[] speechFrame = new short[160];
        Arrays.fill(speechFrame, (short) 500);

        bridge.onAudio(speechFrame, speechFrame.length);

        ArgumentCaptor<short[]> captor = ArgumentCaptor.forClass(short[].class);
        verify(session).sendAudio(captor.capture());
        short[] sent = captor.getValue();

        assertThat(sent[0]).isEqualTo((short) 500);
    }
}
