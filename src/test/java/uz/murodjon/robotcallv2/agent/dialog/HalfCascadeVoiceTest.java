package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.tts.PcmChunkListener;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A HALF_CASCADE call speaks the engine's text with our TTS: in order, sentence by
 * sentence, never a figure the fact guard refuses, and nothing of an answer the caller cut off.
 */
class HalfCascadeVoiceTest {

    private final List<String> spoken = new CopyOnWriteArrayList<>();
    private TtsRouter ttsRouter;
    private DialogPolicy dialogPolicy;
    private DialogMetrics metrics;
    private HalfCascadeVoice voice;

    @BeforeEach
    void setUp() {
        ttsRouter = mock(TtsRouter.class);
        dialogPolicy = mock(DialogPolicy.class);
        metrics = mock(DialogMetrics.class);
        when(dialogPolicy.findFactViolations(anyString(), any(), any(), any())).thenReturn(List.of());
        doAnswer(call -> {
            spoken.add(call.getArgument(0));
            ((PcmChunkListener) call.getArgument(5)).onChunk(new short[160]);
            return null;
        }).when(ttsRouter).synthesizeStreaming(anyString(), anyString(), any(), any(), any(), any());

        RealtimeDialogSession session = new RealtimeDialogSession("chan-1", "uz-UZ", 1L, 1L, null,
                ScenarioFixtures.debtCollection(), null, mock(RtpEndpoint.class), () -> { }, () -> { });
        voice = new HalfCascadeVoice(session, ttsRouter, dialogPolicy, metrics, "nigora",
                EffectiveVoiceSettings.NONE, List.of(), session::startReply);
    }

    @Test
    void speaksEachSentenceAsSoonAsItIsComplete() throws InterruptedException {
        voice.append("Assalomu alaykum, Aziz aka. ");
        voice.append("Qarzingiz bo'yicha qo'ng'iroq qilyapman. Hozir gaplashsak");
        voice.append(" bo'ladimi?");
        voice.flush();
        awaitSpoken();

        assertThat(spoken).containsExactly(
                "Assalomu alaykum, Aziz aka.",
                "Qarzingiz bo'yicha qo'ng'iroq qilyapman.",
                "Hozir gaplashsak bo'ladimi?");
    }

    @Test
    void neverSpeaksASentenceTheFactGuardRefuses() throws InterruptedException {
        when(dialogPolicy.findFactViolations(eq("Qarzingiz 9 000 000 so'm."), any(), any(), any()))
                .thenReturn(List.of("9000000"));

        voice.append("Qarzingiz 9 000 000 so'm.");
        voice.flush();
        awaitSpoken();

        assertThat(spoken).isEmpty();
        verify(metrics).factGuardBlock();
    }

    @Test
    void dropsTheRestOfAnAnswerTheCallerCutOff() throws InterruptedException {
        voice.append("Bu birinchi gap, u aytilmaydi");
        voice.cancel();
        voice.append("Tushunarli, eshitaman.");
        voice.flush();
        awaitSpoken();

        assertThat(spoken).containsExactly("Tushunarli, eshitaman.");
    }

    private void awaitSpoken() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        voice.afterSpoken(done::countDown);
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
