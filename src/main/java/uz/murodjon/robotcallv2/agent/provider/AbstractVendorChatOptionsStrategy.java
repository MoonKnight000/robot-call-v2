package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

/**
 * The steps every vendor shares (Template Method): start from the vendor model's own options,
 * lay the request's model, tools and stop sequences over them, then let the vendor apply the
 * tuning only it knows how to read ({@link #applyTuning}).
 *
 * @param <B> the vendor's options builder
 */
abstract class AbstractVendorChatOptionsStrategy<B extends DefaultToolCallingChatOptions.Builder<B>>
        implements VendorChatOptionsStrategy {

    @Override
    public final ChatOptions toVendorOptions(ChatOptions vendorDefaults, PortableChatOptions request) {
        B builder = newBuilder();
        if (vendorDefaults != null) {
            builder.combineWith(vendorDefaults.mutate());
        }
        String model = request.getModel();
        if (model != null && !model.isBlank()) {
            builder.model(model);
        } else {
            model = vendorDefaults != null ? vendorDefaults.getModel() : null;
        }
        if (request.getToolCallbacks() != null) {
            builder.toolCallbacks(request.getToolCallbacks());
        }
        if (request.getToolContext() != null) {
            builder.toolContext(request.getToolContext());
        }
        if (request.getStopSequences() != null) {
            builder.stopSequences(request.getStopSequences());
        }
        applyTuning(builder, request, model);
        return builder.build();
    }

    /** An empty builder of the vendor's options type. */
    protected abstract B newBuilder();

    /**
     * Token cap, temperature, penalties and thinking, in the vendor's own terms.
     *
     * @param model the model the request will run on, or {@code null} when neither side names one
     */
    protected abstract void applyTuning(B builder, PortableChatOptions request, String model);
}
