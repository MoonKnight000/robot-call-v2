package uz.murodjon.robotcallv2.scenario.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import uz.murodjon.robotcallv2.shared.dialog.InterruptionSensitivity;
import uz.murodjon.robotcallv2.shared.dialog.TranscriptionMode;

import java.util.List;

/**
 * Agent settings a stage replaces while the call is in it — a debt notice read slower, a
 * legal sentence that must not be talked over. Every field is null for "keep the agent's".
 * Applied by the cascade pipeline only: a realtime session fixes these when it opens.
 *
 * @param voiceSpeed              TTS pace, 1.0 = the voice's own; same range as the agent's speed
 * @param interruptionSensitivity how readily the caller's speech cuts the agent off
 * @param knowledgeRetrieval      whether the agent's knowledge base is searched on this stage's turns
 * @param knowledgePassages       how many passages a search hands the model on this stage
 * @param reminderSeconds         caller silence after which the agent asks whether they are still
 *                                there; replaces the deployment's no-input threshold
 * @param reminderMaxCount        how many such reminders before the call is ended as silent; counted
 *                                over the whole call, so a stage that allows fewer than were already
 *                                spent ends the call at its next silence
 * @param responseWaitMs          caller silence, after the caller stops speaking, the agent waits
 *                                before taking its turn; replaces the agent's {@code responseWaitMs}
 * @param transcriptionMode       how the recogniser trades latency for accuracy on this stage's turns
 * @param boostedKeywords         words the recogniser should favour on this stage; REPLACES the agent's
 *                                list (not merged), an empty list clears it, null keeps the agent's
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StageOverrides(
        Double voiceSpeed,
        InterruptionSensitivity interruptionSensitivity,
        Boolean knowledgeRetrieval,
        Integer knowledgePassages,
        Integer reminderSeconds,
        Integer reminderMaxCount,
        Integer responseWaitMs,
        TranscriptionMode transcriptionMode,
        List<String> boostedKeywords
) {
}
