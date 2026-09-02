package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

public record FastPathResult(
        boolean handled,
        String reply,
        String nextStage,
        Disposition disposition,
        boolean endCall
) {
    public static final FastPathResult NOT_HANDLED = new FastPathResult(false, null, null, null, false);

    public static FastPathResult replyAndTransition(String reply, String nextStage) {
        return new FastPathResult(true, reply, nextStage, null, false);
    }

    public static FastPathResult endWithDisposition(String reply, Disposition disposition) {
        return new FastPathResult(true, reply, null, disposition, true);
    }
}
