package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a question about a live call to whichever engine is actually running it.
 *
 * <p>A call belongs to exactly one engine for its whole life — the mode is resolved once,
 * before any media is set up, and never revisited — so "which engine" is a lookup, not a
 * decision. Teardown, the live-calls list and the operator screen ask through here so
 * they never have to know which one it was; the call paths that are specific to a
 * pipeline ({@code startCall}, {@code onClientFinal}, {@code notifyBargeIn}) still go
 * straight to the engine that owns them.
 */
@Component
public class DialogRouter {

    private final DialogEngine cascade;
    private final RealtimeDialogEngine realtime;

    public DialogRouter(DialogEngine cascade, RealtimeDialogEngine realtime) {
        this.cascade = cascade;
        this.realtime = realtime;
    }

    /**
     * The engine running {@code channelId}.
     *
     * <p>Falls back to the cascade engine when neither owns it, so a caller that asks
     * about a call that has already been torn down gets the same empty answers it always
     * did rather than a null to guard against.
     */
    private CallDialog engineOf(String channelId) {
        return realtime.owns(channelId) ? realtime : cascade;
    }

    public DialogOutcome outcome(String channelId) {
        return engineOf(channelId).outcome(channelId);
    }

    public DialogTechnicalSnapshot technicalSnapshot(String channelId) {
        return engineOf(channelId).technicalSnapshot(channelId);
    }

    public void endCall(String channelId) {
        engineOf(channelId).endCall(channelId);
    }

    /** Answering-machine detection runs in both modes, and either engine may be the one to close. */
    public boolean notifyVoicemail(String channelId) {
        return engineOf(channelId).notifyVoicemail(channelId);
    }

    /** Both engines' live calls, in one list — the API does not distinguish them. */
    public List<LiveDialogSnapshot> liveDialogs() {
        List<LiveDialogSnapshot> all = new ArrayList<>(cascade.liveDialogs());
        all.addAll(realtime.liveDialogs());
        return all;
    }

    public OperatorSnapshot operatorSnapshot(String channelId) {
        return engineOf(channelId).operatorSnapshot(channelId);
    }
}
