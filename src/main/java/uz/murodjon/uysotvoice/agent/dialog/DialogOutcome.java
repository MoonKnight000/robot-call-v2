package uz.murodjon.uysotvoice.agent.dialog;

import uz.murodjon.uysotvoice.shared.dialog.Disposition;

/**
 * What a conversation ended up producing, read once at teardown before the dialog
 * session is dropped. Grouped into one record because the pieces have to be read
 * together — by the time teardown runs, the session is gone.
 *
 * @param disposition     final outcome, or {@code null} if the dialog never recorded one
 * @param doNotCallReason why the client asked not to be called again (§11.4), else {@code null}
 */
public record DialogOutcome(Disposition disposition, String doNotCallReason) {

    public static final DialogOutcome NONE = new DialogOutcome(null, null);
}
