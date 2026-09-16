package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Whisper for live call supervision: guidance a supervisor gives the bot while listening
 * in, which the customer never hears. Both engines take it. The takeover half lives in
 * {@code OperatorTransferService#transferToOperator}, which serves both engines too.
 */
@Service
public class LiveCallControlHub {

    private static final Logger log = LoggerFactory.getLogger(LiveCallControlHub.class);

    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;

    public LiveCallControlHub(DialogEngine dialogEngine, RealtimeDialogEngine realtimeDialogEngine) {
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
    }

    /**
     * Hand the bot the guidance of a supervisor for the rest of this call. A cascade dialog
     * carries it in the prompt of every turn from the next one on
     * ({@link SystemPromptFactory#turnAnnex}), a newer whisper replacing it; a realtime
     * engine gets it as context in its conversation ({@link RealtimeDialogEngine#offerWhisper}).
     *
     * @return {@code false} when the call has no bot to steer: it is already with an
     *         operator, or over
     */
    public boolean offerWhisper(String channelId, String operatorInstruction) {
        if (channelId == null || operatorInstruction == null || operatorInstruction.isBlank()) {
            return false;
        }
        String instruction = operatorInstruction.trim();
        DialogSession session = dialogEngine.findSession(channelId);
        if (session != null && !session.isEnded()) {
            session.setOperatorWhisper(instruction);
            log.info("[{}] operator whisper injected ({} chars)", channelId, instruction.length());
            return true;
        }
        return realtimeDialogEngine.offerWhisper(channelId, instruction);
    }

    /** {@link #offerWhisper}, for a caller with nothing else to do when the bot cannot take it. */
    public void injectWhisper(String channelId, String operatorInstruction) {
        if (!offerWhisper(channelId, operatorInstruction)) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, channelId);
        }
    }
}
