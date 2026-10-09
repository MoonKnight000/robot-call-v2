package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.List;
import java.util.Map;

/**
 * Collects a {@link DialogSession}'s per-call configuration; obtained from
 * {@link DialogSession#builder}. Anything not set keeps the default below — a call
 * without audio (chat, simulation) simply leaves the endpoint, watchdog and hangup unset.
 *
 * <p>The §11.1 disclosure defaults to on: a caller who must be told they are speaking to
 * an AI is the case that cannot be forgotten, so turning it off is the explicit step.
 */
public class DialogSessionBuilder {

    final String channelId;
    final String language;
    String ttsVoice;
    CallContext context;
    ScenarioDefinition scenario;
    RtpEndpoint endpoint;
    Runnable hangup;
    Runnable transfer;
    long callAttemptId;
    long companyId;
    NoInputWatchdog watchdog;
    boolean disclosureEnabled = true;
    String companyName;
    String companyDisclosureText;
    EffectiveAiModelConfig aiModel;
    EffectiveVoiceSettings voiceSettings = EffectiveVoiceSettings.NONE;
    boolean emotionAdaptiveVoice = true;
    AgentPersona agentPersona = AgentPersona.AI_ASSISTANT;
    Map<String, String> languageVoices = Map.of();
    String firstMessage;
    List<Tool> tools = List.of();
    Map<String, String> toolMocks;
    List<PronunciationRule> pronunciationRules = List.of();
    AiAgent agent;

    DialogSessionBuilder(String channelId, String language) {
        this.channelId = channelId;
        this.language = language;
    }

    public DialogSessionBuilder ttsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
        return this;
    }

    public DialogSessionBuilder context(CallContext context) {
        this.context = context;
        return this;
    }

    public DialogSessionBuilder scenario(ScenarioDefinition scenario) {
        this.scenario = scenario;
        return this;
    }

    public DialogSessionBuilder endpoint(RtpEndpoint endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public DialogSessionBuilder hangup(Runnable hangup) {
        this.hangup = hangup;
        return this;
    }

    public DialogSessionBuilder transfer(Runnable transfer) {
        this.transfer = transfer;
        return this;
    }

    public DialogSessionBuilder callAttemptId(long callAttemptId) {
        this.callAttemptId = callAttemptId;
        return this;
    }

    public DialogSessionBuilder companyId(long companyId) {
        this.companyId = companyId;
        return this;
    }

    public DialogSessionBuilder watchdog(NoInputWatchdog watchdog) {
        this.watchdog = watchdog;
        return this;
    }

    public DialogSessionBuilder disclosureEnabled(boolean disclosureEnabled) {
        this.disclosureEnabled = disclosureEnabled;
        return this;
    }

    public DialogSessionBuilder companyName(String companyName) {
        this.companyName = companyName;
        return this;
    }

    public DialogSessionBuilder companyDisclosureText(String companyDisclosureText) {
        this.companyDisclosureText = companyDisclosureText;
        return this;
    }

    public DialogSessionBuilder aiModel(EffectiveAiModelConfig aiModel) {
        this.aiModel = aiModel;
        return this;
    }

    public DialogSessionBuilder voiceSettings(EffectiveVoiceSettings voiceSettings) {
        this.voiceSettings = voiceSettings;
        return this;
    }

    public DialogSessionBuilder emotionAdaptiveVoice(boolean emotionAdaptiveVoice) {
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
        return this;
    }

    public DialogSessionBuilder agentPersona(AgentPersona agentPersona) {
        this.agentPersona = agentPersona;
        return this;
    }

    public DialogSessionBuilder languageVoices(Map<String, String> languageVoices) {
        this.languageVoices = languageVoices;
        return this;
    }

    public DialogSessionBuilder firstMessage(String firstMessage) {
        this.firstMessage = firstMessage;
        return this;
    }

    public DialogSessionBuilder tools(List<Tool> tools) {
        this.tools = tools;
        return this;
    }

    /**
     * A simulation's canned tool answers, by tool name: with these set, {@link #tools} are offered to
     * the model but never called — each answers from here (TurnTools). Null on a real call.
     */
    public DialogSessionBuilder toolMocks(Map<String, String> toolMocks) {
        this.toolMocks = toolMocks;
        return this;
    }

    public DialogSessionBuilder pronunciationRules(List<PronunciationRule> pronunciationRules) {
        this.pronunciationRules = pronunciationRules;
        return this;
    }

    public DialogSessionBuilder agent(AiAgent agent) {
        this.agent = agent;
        return this;
    }

    public DialogSession build() {
        return new DialogSession(this);
    }
}
