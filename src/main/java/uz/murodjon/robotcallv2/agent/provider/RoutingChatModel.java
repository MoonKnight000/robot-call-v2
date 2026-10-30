package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * The one {@link ChatModel} the rest of the application sees: each request goes to the vendor
 * that serves the model id it names — Gemini, OpenAI or Claude — so an agent, a stage or the
 * fast-reply model can be on any of them without any caller knowing.
 *
 * <p>The request's options are carried over by the same merge Spring AI's own ChatClient uses
 * ({@code combineWith}): model, token cap, temperature and tool callbacks cross vendors, and a
 * vendor's own settings (Gemini's thinking level) survive when the request was built for that
 * vendor. Callers that pick a vendor by model id therefore need no change when a vendor is
 * added. A request naming no model, or one no vendor recognises, runs on the default vendor.
 */
public class RoutingChatModel implements ChatModel {

    private final ChatModelRegistry chatModelRegistry;
    private final ChatVendor defaultVendor;

    public RoutingChatModel(ChatModelRegistry chatModelRegistry, ChatVendor defaultVendor) {
        this.chatModelRegistry = chatModelRegistry;
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
     * The default vendor's own model and token cap, in the options type the vendors' builders
     * merge into — what a ChatClient starts from before the request's options are laid over it.
     * A model id left blank by a caller is read from here.
     */
    @Override
    public ChatOptions getOptions() {
        ChatOptions defaults = chatModelRegistry.findByVendor(defaultVendor).getOptions();
        return GoogleGenAiChatOptions.builder()
                .model(defaults.getModel())
                .maxOutputTokens(defaults.getMaxTokens())
                .build();
    }

    private Routed route(Prompt prompt) {
        ChatOptions requested = prompt.getOptions();
        ChatVendor vendor = requested == null ? null : ChatVendor.ofModel(requested.getModel());
        boolean routable = chatModelRegistry.isAvailable(vendor);
        ChatModel model = chatModelRegistry.findByVendor(routable ? vendor : defaultVendor);
        if (requested == null) {
            return new Routed(model, prompt);
        }
        ChatOptions.Builder<?> overrides = requested.mutate();
        if (!routable) {
            // The id names a vendor this deployment has no key for (or nobody): the default
            // vendor answers on its own model rather than being sent an id it does not know.
            overrides.model(null);
        }
        ChatOptions options = model.getOptions().mutate().combineWith(overrides).build();
        return new Routed(model, prompt.mutate().chatOptions(options).build());
    }

    private record Routed(ChatModel model, Prompt prompt) {
    }
}
