package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * A structured data field the AI Agent should extract from the caller during a conversation.
 * One field the call is expected to come back with.
 *
 * @param id          unique field identifier (e.g. "caller_name", "callback_number")
 * @param type        data type (e.g. "String", "Number", "Boolean")
 * @param name        human-readable display label
 * @param description extraction instructions for the LLM
 */
public record DataExtractionField(
        String id,
        String type,
        String name,
        String description
) {
}
