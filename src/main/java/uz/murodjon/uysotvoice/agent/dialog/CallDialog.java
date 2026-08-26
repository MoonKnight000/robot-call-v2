package uz.murodjon.uysotvoice.agent.dialog;

import java.util.List;

/**
 * What every conversation offers the rest of the system, whichever engine is running it:
 * end it, read what it produced, look at it while it is live.
 *
 * <p>Only the parts that are genuinely the same for both pipelines. Starting a call is
 * not here — a cascade call is started with a TTS voice and returns nothing, a realtime
 * call is started without one and returns the audio bridge that feeds its engine — and
 * neither is {@code onClientFinal} or {@code notifyBargeIn}, which exist because the
 * cascade pipeline has to be told things a realtime engine works out for itself.
 * Flattening those into a common interface would mean half its methods throwing on half
 * its implementations.
 *
 * <p>Implemented by {@link DialogEngine} and {@link RealtimeDialogEngine}; dispatched by
 * {@link DialogRouter}.
 */
public interface CallDialog {

    /** Whether this engine could run a call at all right now. */
    boolean available();

    /** Whether this engine is the one running {@code channelId}. */
    boolean owns(String channelId);

    /** What the conversation produced, read at teardown before the session is dropped. */
    DialogOutcome outcome(String channelId);

    /** What the conversation accumulated for the "Texnik" tab (§10.5), read at teardown. */
    DialogTechnicalSnapshot technicalSnapshot(String channelId);

    /** Close the conversation as answered by a machine (§8.6); false if there was none to close. */
    boolean notifyVoicemail(String channelId);

    /** Drop the conversation and release whatever it holds. */
    void endCall(String channelId);

    /** Every call this engine still has in conversation (§10.2). */
    List<LiveDialogSnapshot> liveDialogs();

    /** Context for the operator a call was transferred to (§11.6), or null if this engine has no such call. */
    OperatorSnapshot operatorSnapshot(String channelId);
}
