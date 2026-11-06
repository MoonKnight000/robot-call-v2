package uz.murodjon.robotcallv2.agent.realtime;

import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;

/** Builder for {@link RealtimeCallConfig}; entry points are {@code builder()} and {@code toBuilder()}. */
public final class RealtimeCallConfigBuilder {

    private String channelId;
    private String language;
    private String systemPrompt;
    private String voice;
    private List<ToolCallback> tools;
    private String pipecatStt;
    private String pipecatLlm;
    private String pipecatTts;
    private String model;
    private ReasoningEffort reasoningEffort;

    RealtimeCallConfigBuilder() {
    }

    public RealtimeCallConfigBuilder channelId(String channelId) {
        this.channelId = channelId;
        return this;
    }

    public RealtimeCallConfigBuilder language(String language) {
        this.language = language;
        return this;
    }

    public RealtimeCallConfigBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public RealtimeCallConfigBuilder voice(String voice) {
        this.voice = voice;
        return this;
    }

    public RealtimeCallConfigBuilder tools(List<ToolCallback> tools) {
        this.tools = tools;
        return this;
    }

    public RealtimeCallConfigBuilder pipecatStt(String pipecatStt) {
        this.pipecatStt = pipecatStt;
        return this;
    }

    public RealtimeCallConfigBuilder pipecatLlm(String pipecatLlm) {
        this.pipecatLlm = pipecatLlm;
        return this;
    }

    public RealtimeCallConfigBuilder pipecatTts(String pipecatTts) {
        this.pipecatTts = pipecatTts;
        return this;
    }

    public RealtimeCallConfigBuilder model(String model) {
        this.model = model;
        return this;
    }

    public RealtimeCallConfigBuilder reasoningEffort(ReasoningEffort reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
        return this;
    }

    public RealtimeCallConfig build() {
        return new RealtimeCallConfig(channelId, language, systemPrompt, voice, tools,
                pipecatStt, pipecatLlm, pipecatTts, model, reasoningEffort);
    }
}
