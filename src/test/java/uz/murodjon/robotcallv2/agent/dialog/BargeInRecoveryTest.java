package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * A false barge-in that cut a reply before its first word leaves nothing to resume and the
 * caller's words unanswered. On a real call the answer to them never ran: the bot sat silent
 * for 8 s until the no-input prompt.
 */
class BargeInRecoveryTest {

    private static final int TURN = 3;

    private final DialogProperties dialogProperties = mock(DialogProperties.class);
    private final DialogExecutors executors = mock(DialogExecutors.class);
    private final SpeechOutput speech = mock(SpeechOutput.class);
    private final DialogSession session = mock(DialogSession.class);
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private final BargeInRecovery recovery = new BargeInRecovery(dialogProperties, executors,
            mock(DialogMetrics.class), speech, mock(DialogTranscript.class));

    @BeforeEach
    void setUp() {
        when(dialogProperties.falseInterruptionTimeoutMs()).thenReturn(1500);
        // The check runs at once rather than 1.5 s later.
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executors).scheduleOnWorker(any(Runnable.class), anyLong());
        RtpEndpoint endpoint = mock(RtpEndpoint.class);
        when(session.endpoint()).thenReturn(endpoint);
        when(session.channelId()).thenReturn("1793782920.45");
        when(session.busy()).thenReturn(busy);
        when(session.turnCount()).thenReturn(TURN);
    }

    @Test
    void nothingToResumeAnswersTheHeldWords() {
        when(session.takeUnspokenText()).thenReturn(null);
        when(session.hasUnansweredClientText()).thenReturn(true);
        AtomicInteger answered = new AtomicInteger();
        AtomicBoolean busyWhenAnswered = new AtomicBoolean(true);

        recovery.scheduleResume(session, TURN, () -> {
            answered.incrementAndGet();
            busyWhenAnswered.set(busy.get());
        });

        assertThat(answered).hasValue(1);
        assertThat(busyWhenAnswered).as("the new turn must be able to claim the call").isFalse();
        verify(speech, never()).speakChunk(any(), any());
    }

    @Test
    void nothingToResumeAndNothingHeldStaysQuiet() {
        when(session.takeUnspokenText()).thenReturn(null);
        when(session.hasUnansweredClientText()).thenReturn(false);
        AtomicInteger answered = new AtomicInteger();

        recovery.scheduleResume(session, TURN, answered::incrementAndGet);

        assertThat(answered).hasValue(0);
        assertThat(busy).isFalse();
    }

    @Test
    void aResumedTailIsSpokenInsteadOfANewTurn() {
        when(session.takeUnspokenText()).thenReturn("Hozir gaplashishga qulaymi?");
        when(speech.speakChunk(session, "Hozir gaplashishga qulaymi?")).thenReturn(true);
        AtomicInteger answered = new AtomicInteger();

        recovery.scheduleResume(session, TURN, answered::incrementAndGet);

        verify(speech).speakChunk(session, "Hozir gaplashishga qulaymi?");
        verify(session).setUnansweredClientText(null);
        assertThat(answered).hasValue(0);
        assertThat(busy).isFalse();
    }

    /*
     * The context a barge-in leaves behind. The system prompt is not in the history — it is built
     * again for every request (SystemPromptFactory) — so a barge-in can only lose context by
     * editing the history wrongly: these pin that it touches the cut-off line and nothing before it.
     */

    @Test
    void stitchingRemovesOnlyTheCutOffCallerLine() {
        List<Message> history = new ArrayList<>(List.of(
                new UserMessage("Alo"),
                new AssistantMessage("Assalomu alaykum, to'lov haqida qo'ng'iroq qilyapman."),
                new UserMessage("Men ertaga")));
        when(session.history()).thenReturn(history);
        when(session.takeUnansweredClientText()).thenReturn("Men ertaga");

        String stitched = recovery.stitchUnansweredUtterance(session, "to'layman");

        assertThat(stitched).isEqualTo("Men ertaga to'layman");
        assertThat(history).extracting(Message::getText)
                .containsExactly("Alo", "Assalomu alaykum, to'lov haqida qo'ng'iroq qilyapman.");
    }

    @Test
    void aResumedTailJoinsTheLastReplyAndLeavesEarlierTurnsAlone() {
        List<Message> history = new ArrayList<>(List.of(
                new UserMessage("Alo"),
                new AssistantMessage("Assalomu alaykum,")));
        when(session.history()).thenReturn(history);
        when(session.takeUnspokenText()).thenReturn("hozir gaplashishga qulaymi?");
        when(speech.speakChunk(session, "hozir gaplashishga qulaymi?")).thenReturn(true);

        recovery.scheduleResume(session, TURN, () -> { });

        assertThat(history).extracting(Message::getText)
                .containsExactly("Alo", "Assalomu alaykum, hozir gaplashishga qulaymi?");
    }
}
