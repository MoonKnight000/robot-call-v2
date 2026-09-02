package uz.murodjon.robotcallv2.agent.realtime;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * What one call tells a {@link RealtimeProvider} about itself when the session opens —
 * the realtime equivalent of the language, system prompt and tool list {@code
 * DialogEngine} assembles per turn in the cascade pipeline. Resolved once: a realtime
 * engine is instructed at connect time, not on every turn.
 *
 * @param channelId    the Asterisk channel this conversation belongs to — for logging
 *                     and MDC, so a provider's own logs join the rest of the call's
 * @param language     BCP-47 language the conversation is held in (e.g. {@code uz-UZ})
 * @param systemPrompt the instructions the engine runs under, built the same way the
 *                     cascade pipeline builds them ({@code SystemPromptFactory})
 * @param voice        provider-side voice name, or {@code null} for the engine's default
 * @param tools        the tools the engine may call, reused as-is from the cascade
 *                     pipeline. They are passed here to be <em>declared</em> to the
 *                     engine, not to be invoked by it: a call arrives at
 *                     {@link RealtimeListener#onToolCall} and is executed by the dialog
 *                     driver, because tool bodies hit the DB and the CRM and must not
 *                     run on the provider's network thread
 */
public record RealtimeCallConfig(
        String channelId,
        String language,
        String systemPrompt,
        String voice,
        List<ToolCallback> tools
) {
}
