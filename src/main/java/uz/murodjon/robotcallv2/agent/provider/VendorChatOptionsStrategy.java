package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Turns a turn's vendor-neutral options into one vendor's request options (Strategy): which
 * token-cap field the vendor reads, whether it takes a temperature, and how
 * {@link uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort} becomes its thinking setting —
 * moved to the nearest level the chosen model accepts, so an agent's setting never makes a
 * vendor answer 400. {@link RoutingChatModel} picks the strategy by {@link ChatVendor}.
 */
public interface VendorChatOptionsStrategy {

    ChatVendor vendor();

    /**
     * @param vendorDefaults the vendor model's own options (key, base URL, YAML tuning) the
     *                       request is laid over; may be {@code null}
     * @param request        the turn's options; a {@code null} model keeps the vendor's default model
     * @return the vendor's options, ready for its chat model
     */
    ChatOptions toVendorOptions(ChatOptions vendorDefaults, PortableChatOptions request);
}
