package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hub for live call supervision:
 * <ul>
 *   <li>Whisper mode: Inject real-time operator instructions into the active LLM context
 *   <li>Takeover mode: Seamlessly bridge the live call from AI to a human operator
 *   <li>Live eavesdropping channel tracking
 * </ul>
 */
@Service
public class LiveCallControlHub {

    private static final Logger log = LoggerFactory.getLogger(LiveCallControlHub.class);

    private final DialogEngine dialogEngine;
    private final Map<String, String> pendingWhispers = new ConcurrentHashMap<>();

    public LiveCallControlHub(DialogEngine dialogEngine) {
        this.dialogEngine = dialogEngine;
    }

    /**
     * Injects an operator whisper guidance note into the active call session.
     * The AI agent will follow this instruction in its very next utterance.
     */
    public void injectWhisper(String channelId, String operatorInstruction) {
        if (channelId == null || operatorInstruction == null || operatorInstruction.isBlank()) {
            return;
        }
        DialogSession session = dialogEngine.findSession(channelId);
        if (session == null || session.isEnded()) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, channelId);
        }

        // Attach operator whisper into CallContext facts
        session.context().facts().put("operatorNotes", "JONLI KO'RSATMA: " + operatorInstruction.trim());
        pendingWhispers.put(channelId, operatorInstruction.trim());
        log.info("[{}] operator whisper injected: \"{}\"", channelId, operatorInstruction.trim());
    }

    /**
     * Instantly halts the AI dialog and triggers a transfer/takeover to a human operator.
     */
    public void takeoverCall(String channelId, String operatorExtension) {
        DialogSession session = dialogEngine.findSession(channelId);
        if (session == null || session.isEnded()) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, channelId);
        }

        log.info("[{}] human operator taking over call (target ext={})", channelId, operatorExtension);
        if (session.transfer() != null) {
            session.transfer().run();
        }
    }
}
