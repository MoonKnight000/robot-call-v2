package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.List;

/**
 * A structured data field the AI Agent should extract from the caller during a conversation.
 * One field the call is expected to come back with.
 *
 * @param id             unique field identifier (e.g. "caller_name", "callback_number")
 * @param type           data type (e.g. "String", "Number", "Boolean")
 * @param name           human-readable display label
 * @param description    extraction instructions for the LLM
 * @param options        the allowed values of an {@code enum} field; the answer must be one of them.
 *                       Empty for an enum whose choices are spelled out in the description
 * @param formatExamples sample answers showing the shape the value should take ("+998 90 123 45 67");
 *                       empty when the description is enough
 * @param required       the field may not be left empty: a missing value is asked for once more
 *                       (Retell's "Allow empty value" switched off). {@code false} keeps today's behaviour
 * @param contactField   key of the contact's custom field the value is also saved to after the call;
 *                       {@code null} — saved with the call only
 */
public record DataExtractionField(
        String id,
        String type,
        String name,
        String description,
        List<String> options,
        List<String> formatExamples,
        boolean required,
        String contactField
) {

    public DataExtractionField {
        options = options == null ? List.of() : List.copyOf(options);
        formatExamples = formatExamples == null ? List.of() : List.copyOf(formatExamples);
        contactField = contactField == null || contactField.isBlank() ? null : contactField.trim();
    }

    /** A field with a fixed list of values and no extraction extras. */
    public DataExtractionField(String id, String type, String name, String description, List<String> options) {
        this(id, type, name, description, options, List.of(), false, null);
    }

    /** A field without a fixed list of values. */
    public DataExtractionField(String id, String type, String name, String description) {
        this(id, type, name, description, List.of());
    }
}
