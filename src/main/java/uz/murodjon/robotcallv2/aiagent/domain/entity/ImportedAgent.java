package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.List;

/**
 * An agent read from an export file, ready to be created as a new draft.
 *
 * @param agent   the agent with everything tied to the exporting company taken out
 * @param removed what was taken out, as the field path it was at (e.g. {@code callBehaviour.transferQueueId},
 *                {@code functions.lookupOrder}); empty when the file carried nothing company-bound
 */
public record ImportedAgent(AiAgent agent, List<String> removed) {

    public ImportedAgent {
        removed = removed == null ? List.of() : List.copyOf(removed);
    }
}
