package uz.murodjon.robotcallv2.contact.application.port.output;

import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.MergedFieldValue;

/** Merges an ACCUMULATE field's stored and new value with an LLM. */
public interface ContactFieldMergerPort {

    /**
     * @param model the model to merge with; {@code null} — the platform's summary model
     * @return the merge, or {@code null} when no model answered with a usable text
     */
    MergedFieldValue generateMergedValue(long companyId, String model, ContactField field, String stored, String incoming);
}
