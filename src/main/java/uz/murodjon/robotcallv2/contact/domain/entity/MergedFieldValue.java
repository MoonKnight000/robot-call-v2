package uz.murodjon.robotcallv2.contact.domain.entity;

/**
 * The model's merge of a contact field's stored and new value.
 *
 * @param ownLlm whether the company's own LLM did the merge — the platform charges nothing then
 */
public record MergedFieldValue(
        String value,
        String model,
        long promptTokens,
        long completionTokens,
        boolean ownLlm
) {
}
