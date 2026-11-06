package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * The one {@link ChatModel} the rest of the application sees: each request goes to the vendor
 * that serves the model id it names — Gemini, OpenAI or Claude — so an agent, a stage or the
 * fast-reply model can be on any of them without any caller knowing.
 *
 * <p>A request in {@link PortableChatOptions} — every dialog turn, and whatever a ChatClient
 * built on this model sends, since {@link #getOptions} is of that type — is turned into the
 * vendor's own options by that vendor's {@link VendorChatOptionsStrategy}: the token-cap field
 * it reads, its thinking setting, what it refuses. A request already in one vendor's options
 * keeps the old merge ({@code combineWith}): the common fields cross vendors, the vendor's own
 * survive only on that vendor. A request naming no model, or one no vendor recognises, runs on
 * the default vendor.
 */
public class RoutingChatModel implements ChatModel {

    private final ChatModelRegistry chatModelRegistry;
    private final VendorChatOptionsStrategies strategies;
    private final ChatVendor defaultVendor;

    public RoutingChatModel(ChatModelRegistry chatModelRegistry, VendorChatOptionsStrategies strategies,
                            ChatVendor defaultVendor) {
        this.chatModelRegistry = chatModelRegistry;
        this.strategies = strategies;
        this.defaultVendor = defaultVendor;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Routed routed = route(prompt);
        return routed.model().call(routed.prompt());
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        Routed routed = route(prompt);
        return routed.model().stream(routed.prompt());
    }

    /**
     * The default vendor's own model and token cap, in the vendor-neutral type — what a ChatClient
     * starts from before the request's options are laid over it, and the type that keeps a turn's
     * thinking level through that merge. A model id left blank by a caller is read from here.
     */
    @Override
    public ChatOptions getOptions() {
        ChatOptions defaults = chatModelRegistry.findByVendor(defaultVendor).getOptions();
        return PortableChatOptions.builder()
                .model(defaults.getModel())
                .maxTokens(defaults.getMaxTokens())
                .build();
    }

    private Routed route(Prompt prompt) {
        ChatOptions requested = prompt.getOptions();
        ChatVendor vendor = requested == null ? null : ChatVendor.ofModel(requested.getModel());
        boolean routable = chatModelRegistry.isAvailable(vendor);
        ChatVendor target = routable ? vendor : defaultVendor;
        ChatModel model = chatModelRegistry.findByVendor(target);
        if (requested == null) {
            return new Routed(model, prompt);
        }
        ChatOptions options;
        if (requested instanceof PortableChatOptions portable) {
            // The id names a vendor this deployment has no key for (or nobody): the default
            // vendor answers on its own model rather than being sent an id it does not know.
            PortableChatOptions request = routable ? portable : portable.mutate().model(null).build();
            options = strategies.findByVendor(target).toVendorOptions(model.getOptions(), request);
        } else {
            ChatOptions.Builder<?> overrides = requested.mutate();
            if (!routable) {
                overrides.model(null);
            }
            options = model.getOptions().mutate().combineWith(overrides).build();
        }
        return new Routed(model, prompt.mutate().chatOptions(options).build());
    }

    private record Routed(ChatModel model, Prompt prompt) {
    }
}
