package uz.murodjon.robotcallv2.callrecord.domain.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lays a fresh agent analysis over a call's stored outcome. Only the analysis keys change:
 * the summary's own fields — {@code reasonCode}, {@code promisedDate}, the scenario's
 * outcome fields — stay as the call left them, so reports and the CRM still agree.
 */
public final class OutcomeAnalysisMerger {

    private OutcomeAnalysisMerger() {
    }

    /**
     * @param analysisKeys every key the analysis owns — the agent's {@code dataNeeded} keys,
     *                     {@code evaluation} and {@code extractionErrors}
     * @param fresh        the outcome the new analysis produced; a key it left out is removed,
     *                     so a value the model no longer finds does not linger from the old run
     */
    public static Map<String, Object> mergeAnalysis(Map<String, Object> stored, Map<String, Object> fresh,
                                                    Collection<String> analysisKeys) {
        Map<String, Object> merged = new LinkedHashMap<>(stored);
        for (String key : analysisKeys) {
            Object value = fresh.get(key);
            if (value == null) {
                merged.remove(key);
            } else {
                merged.put(key, value);
            }
        }
        return merged;
    }
}
