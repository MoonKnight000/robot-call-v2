package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.aiagent.domain.enums.InterruptionSensitivity;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ClientInputGateTest {

    private DialogProperties dialogProperties;
    private SttProperties sttProperties;
    private LanguageDetector languageDetector;
    private DialogMetrics metrics;
    private DialogTranscript transcript;
    private TurnRunner turnRunner;
    private DialogExecutors executors;
    private DialogTuningProperties dialogTuning;
    private LlmTurnClient llmTurnClient;
    private VoicemailDrop voicemailDrop;
    private CallMetrics callMetrics;
    private FloorLearning floorLearning;

    private ClientInputGate gate;
    private DialogSession session;
    private RtpEndpoint endpoint;
    private AiAgent agent;

    @BeforeEach
    void setUp() {
        dialogProperties = new DialogProperties();
        sttProperties = mock(SttProperties.class);
        languageDetector = mock(LanguageDetector.class);
        metrics = mock(DialogMetrics.class);
        transcript = mock(DialogTranscript.class);
        turnRunner = mock(TurnRunner.class);
        // Direct synchronous executor for deterministic testing
        executors = mock(DialogExecutors.class);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executors).submit(any(Runnable.class));

        dialogTuning = new DialogTuningProperties();
        llmTurnClient = mock(LlmTurnClient.class);
        voicemailDrop = mock(VoicemailDrop.class);
        callMetrics = mock(CallMetrics.class);
        floorLearning = mock(FloorLearning.class);

        gate = new ClientInputGate(dialogProperties, sttProperties, languageDetector, metrics, transcript,
                turnRunner, executors, dialogTuning, llmTurnClient, voicemailDrop, callMetrics, floorLearning);

        session = mock(DialogSession.class);
        endpoint = mock(RtpEndpoint.class);
        agent = mock(AiAgent.class);

        AiAgentCallBehaviour behaviour = mock(AiAgentCallBehaviour.class);
        when(behaviour.interruptionSensitivity()).thenReturn(InterruptionSensitivity.MEDIUM);
        when(agent.callBehaviour()).thenReturn(behaviour);

        when(session.agent()).thenReturn(agent);
        when(session.endpoint()).thenReturn(endpoint);
        when(session.channelId()).thenReturn("PJSIP/channel-123");
        when(session.busy()).thenReturn(new AtomicBoolean(false));
    }

    @Test
    void backchannelWhileBargeInPendingResumesBotAndDoesNotAdvanceTurn() {
        when(session.isBargeInPending()).thenReturn(true);
        when(session.settleBargeIn()).thenReturn(true);

        gate.onFinal(session, "Aha, xo'p.");

        verify(endpoint).resumePlayback();
        verify(metrics).backchannelIgnored();
        verify(turnRunner, never()).advance(any(), anyString(), anyBoolean());
        verify(endpoint, never()).flushPlayback();
    }

    @Test
    void tushunarliWhileBargeInPendingResumesBotAndDoesNotAdvanceTurn() {
        when(session.isBargeInPending()).thenReturn(true);
        when(session.settleBargeIn()).thenReturn(true);

        gate.onFinal(session, "Tushunarli, mayli.");

        verify(endpoint).resumePlayback();
        verify(metrics).backchannelIgnored();
        verify(turnRunner, never()).advance(any(), anyString(), anyBoolean());
    }

    @Test
    void definitiveQuestionInterruptsAndAdvancesTurn() {
        when(session.isBargeInPending()).thenReturn(true);
        when(session.settleBargeIn()).thenReturn(true);

        gate.onFinal(session, "To'xtang, qancha to'lashim kerak?");

        verify(endpoint).flushPlayback();
        verify(session).setInterrupted(true);
        verify(turnRunner).advance(eq(session), eq("To'xtang, qancha to'lashim kerak?"), eq(true));
        verify(endpoint, never()).resumePlayback();
    }

    @Test
    void definitiveNoInterruptsAndAdvancesTurn() {
        when(session.isBargeInPending()).thenReturn(true);
        when(session.settleBargeIn()).thenReturn(true);

        gate.onFinal(session, "Yo'q, adashdingiz!");

        verify(endpoint).flushPlayback();
        verify(session).setInterrupted(true);
        verify(turnRunner).advance(eq(session), eq("Yo'q, adashdingiz!"), eq(true));
    }

    @Test
    void interimBackchannelDoesNotTriggerBargeIn() {
        when(session.isBargeInPending()).thenReturn(true);

        gate.onInterim(session, "aha");

        verify(endpoint, never()).flushPlayback();
        verify(session, never()).setInterrupted(true);
    }

    @Test
    void interimWithMultipleWordsConfirmsBargeIn() {
        when(session.isBargeInPending()).thenReturn(true);
        when(session.settleBargeIn()).thenReturn(true);

        // "to'xtang bir daqiqa eshiting" has 4 non-backchannel words -> exceeds minInterruptionWords (default 2)
        gate.onInterim(session, "to'xtang bir daqiqa eshiting");

        verify(endpoint).flushPlayback();
        verify(session).setInterrupted(true);
    }
}
