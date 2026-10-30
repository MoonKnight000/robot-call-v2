package uz.murodjon.robotcallv2.agent.provider;

import java.util.Locale;

/**
 * The text-LLM vendors a cascade turn can run on. The same three the model catalog files its
 * {@code LLM}/{@code CASCADE} rows under: {@link #provider()} is the {@code ai_model.provider}
 * value.
 */
public enum ChatVendor {

    GOOGLE_GENAI("google-genai"),
    OPENAI("openai"),
    ANTHROPIC("anthropic");

    private final String provider;

    ChatVendor(String provider) {
        this.provider = provider;
    }

    /** The {@code ai_model.provider} and {@code spring.ai.model.chat} value of this vendor. */
    public String provider() {
        return provider;
    }

    /** @return the vendor with this provider id, or {@code null} when it is not a chat vendor */
    public static ChatVendor ofProvider(String provider) {
        if (provider == null) {
            return null;
        }
        for (ChatVendor vendor : values()) {
            if (vendor.provider.equalsIgnoreCase(provider.trim())) {
                return vendor;
            }
        }
        return null;
    }

    /**
     * The vendor that serves a model id, told from the id alone — the ids of the three families
     * never overlap. Names an OpenAI-compatible endpoint serves ({@code llama}, {@code mixtral},
     * {@code qwen}) go to {@link #OPENAI}, the slot {@code OPENAI_BASE_URL} repoints.
     *
     * @return the vendor, or {@code null} for a blank or unrecognised id
     */
    public static ChatVendor ofModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        String id = modelId.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("gemini")) {
            return GOOGLE_GENAI;
        }
        if (id.startsWith("claude")) {
            return ANTHROPIC;
        }
        if (id.startsWith("gpt-") || id.startsWith("llama") || id.startsWith("mixtral") || id.startsWith("qwen")) {
            return OPENAI;
        }
        return null;
    }
}
