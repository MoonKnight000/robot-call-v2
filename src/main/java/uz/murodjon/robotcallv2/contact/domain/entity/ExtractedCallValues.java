package uz.murodjon.robotcallv2.contact.domain.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a finished call's post-call extraction found, for the contact it was about.
 *
 * @param callAttemptId   the call, for the billing of an ACCUMULATE merge
 * @param mergeModel      the model ACCUMULATE fields are merged with (the agent's fast model);
 *                        {@code null} — the platform's summary model
 * @param byExtractionKey every extracted value, by the extraction field's key — what a company
 *                        contact field's {@code extractionKey} names
 * @param byContactField  values the agent itself routes to a contact field key (its
 *                        {@code dataNeeded[].contactField}); a company mapping of the same key wins
 */
public record ExtractedCallValues(
        long callAttemptId,
        String mergeModel,
        Map<String, String> byExtractionKey,
        Map<String, String> byContactField
) {

    public ExtractedCallValues {
        byExtractionKey = byExtractionKey == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(byExtractionKey));
        byContactField = byContactField == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(byContactField));
    }

    public boolean isEmpty() {
        return byExtractionKey.isEmpty() && byContactField.isEmpty();
    }
}
