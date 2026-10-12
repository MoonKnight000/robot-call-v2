package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperatorCallRecorderTest {

    private static final String CLIENT = "client-1";
    private static final String OPERATOR = "operator-1";

    private ARI ari;
    private CallMediaFactory media;
    private CallSessionRegistry sessions;
    private RtpEndpoint recording;
    private MediaTap clientTap;
    private MediaTap operatorTap;
    private OperatorCallTranscriber transcriber;
    private OperatorCallRecorder recorder;

    @BeforeEach
    void setUp() throws Exception {
        ari = mock(ARI.class);
        AriClient ariClient = mock(AriClient.class);
        when(ariClient.findConnection()).thenReturn(ari);
        media = mock(CallMediaFactory.class);
        sessions = mock(CallSessionRegistry.class);
        recording = mock(RtpEndpoint.class);
        CallSession session = mock(CallSession.class);
        when(session.endpoint()).thenReturn(recording);
        when(sessions.find(CLIENT)).thenReturn(session);
        when(sessions.contains(CLIENT)).thenReturn(true);
        clientTap = tap("client");
        operatorTap = tap("operator");
        when(media.openTap(eq(ari), eq(CLIENT), any())).thenReturn(clientTap);
        when(media.openTap(eq(ari), eq(OPERATOR), any())).thenReturn(operatorTap);
        transcriber = mock(OperatorCallTranscriber.class);
        recorder = new OperatorCallRecorder(ariClient, media, sessions, transcriber, mock(CallAttemptUseCase.class));
    }

    @Test
    void operatorJoiningSplitsTheRecordingIntoTwoTaps() throws Exception {
        recorder.attach(CLIENT, OPERATOR);

        verify(recording).setRecordingInbound(false);
        verify(media).openTap(eq(ari), eq(CLIENT), any());
        verify(media).openTap(eq(ari), eq(OPERATOR), any());
        verify(recording, never()).setRecordingInbound(true);
    }

    @Test
    void bothSidesGetARecognizerNextToTheRecording() {
        when(transcriber.openFor(any(), eq(OperatorCallTranscriber.CLIENT))).thenReturn(mock(AudioListener.class));
        when(transcriber.openFor(any(), eq(OperatorCallTranscriber.OPERATOR))).thenReturn(mock(AudioListener.class));

        recorder.attach(CLIENT, OPERATOR);

        verify(clientTap.endpoint()).replaceListeners(argThat(listeners -> listeners.size() == 2));
        verify(operatorTap.endpoint()).replaceListeners(argThat(listeners -> listeners.size() == 2));
    }

    @Test
    void operatorLeavingClosesBothTapsAndRecordsTheCallAgain() {
        recorder.attach(CLIENT, OPERATOR);

        recorder.release(OPERATOR);

        verify(media).releaseTap(ari, clientTap);
        verify(media).releaseTap(ari, operatorTap);
        verify(recording).setRecordingInbound(true);
    }

    @Test
    void transferKeepsTheClientTapAndReplacesTheOperatorOne() throws Exception {
        MediaTap colleagueTap = tap("colleague");
        when(media.openTap(eq(ari), eq("colleague-1"), any())).thenReturn(colleagueTap);
        recorder.attach(CLIENT, OPERATOR);

        recorder.attach(CLIENT, "colleague-1");
        recorder.release(OPERATOR);

        verify(media, times(1)).openTap(eq(ari), eq(CLIENT), any());
        verify(media).releaseTap(ari, operatorTap);
        verify(media, never()).releaseTap(ari, clientTap);
    }

    @Test
    void tapThatCannotOpenLeavesTheCallRecordedMixed() throws Exception {
        when(media.openTap(eq(ari), eq(OPERATOR), any())).thenThrow(new RuntimeException("snoop refused"));

        recorder.attach(CLIENT, OPERATOR);

        verify(media).releaseTap(ari, clientTap);
        verify(recording).setRecordingInbound(true);
    }

    @Test
    void clientGoneWhileTapsOpenedReleasesThem() {
        when(sessions.contains(CLIENT)).thenReturn(false);

        recorder.attach(CLIENT, OPERATOR);

        verify(media).releaseTap(ari, clientTap);
        verify(media).releaseTap(ari, operatorTap);
    }

    private static MediaTap tap(String name) {
        return new MediaTap(name + "-snoop", name + "-ext", name + "-bridge", mock(RtpEndpoint.class), 0);
    }
}
