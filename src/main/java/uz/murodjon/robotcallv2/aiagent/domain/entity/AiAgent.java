package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentChannel;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AiAgentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An AI agent: the voice, persona, model, speech engine and limits a call or chat is run with.
 *
 * <p>Supports Retell-style paradigms:
 * <ul>
 *   <li>{@link AiAgentMode#SINGLE_PROMPT} or {@link AiAgentMode#CONVERSATIONAL_FLOW}</li>
 *   <li>{@link AiAgentChannel#VOICE}, {@link AiAgentChannel#CHAT}, or {@link AiAgentChannel#OMNICHANNEL}</li>
 * </ul>
 *
 * <p>Most of what an agent is arrives in groups — {@link AiAgentVoice}, {@link
 * AiAgentSpeechEngine}, {@link AiAgentAmbience}, {@link AiAgentCallBehaviour}, {@link
 * AiAgentLimits}, {@link AiAgentDataPolicy}, {@link AiAgentScript}. Each of those owns its
 * own defaults, so an agent read from a half-filled row still comes out usable, and each
 * groups settings that are only meaningful together. What stays here is what identifies
 * the agent, what every part of it needs (the language, the persona), and the model.
 *
 * <p>{@code publishedVersion} is set only on an agent read from a published snapshot
 * (NEXT-PLAN A.1); the draft row an editor works on has {@code null} here.
 *
 * <p>{@code version} is the draft row's optimistic-lock counter as read — not a published
 * version: a write goes through only while the row still has it. A snapshot does not keep
 * it ({@code AiAgentSnapshot}), so it reads back as 0.
 */
public record AiAgent(
        Long id,
        Long companyId,
        String name,
        String description,
        String language,
        List<String> speechLanguages,
        AiAgentChannel channel,
        AiAgentMode mode,
        AgentPersona persona,
        AiAgentScript script,
        AiAgentSpeechEngine speechEngine,
        AiAgentVoice voice,
        AiAgentAmbience ambience,
        AgentGreeting greeting,
        AiAgentCallBehaviour callBehaviour,
        AiAgentLimits limits,
        AiAgentDataPolicy dataPolicy,
        String llmModel,
        String fastLlmModel,
        Double temperature,
        Integer maxOutputTokens,
        boolean useRag,
        List<DataExtractionField> dataNeeded,
        List<DataEvaluationCriterion> dataEvaluation,
        AgentWebhookConfig initiationWebhook,
        AgentWebhookConfig postCallWebhook,
        List<PronunciationRule> pronunciationRules,
        List<PostCallAction> postCallActions,
        List<WorkflowFunction> preCallFunctions,
        Set<HandbookRule> handbook,
        String timezone,
        Set<Long> sipTrunkIds,
        boolean enabled,
        Instant createdAt,
        Long createdBy,
        Integer publishedVersion,
        long version
) {

    public AiAgent {
        Objects.requireNonNull(name, "Agent name cannot be null");
        if (language == null || language.isBlank()) {
            language = "uz-UZ";
        }
        // The agent's own language always leads; the rest are the extra languages a call may switch to.
        Set<String> spoken = new LinkedHashSet<>();
        spoken.add(language);
        if (speechLanguages != null) {
            for (String extra : speechLanguages) {
                if (extra != null && !extra.isBlank()) {
                    spoken.add(extra.trim());
                }
            }
        }
        speechLanguages = List.copyOf(spoken);
        if (channel == null) {
            channel = AiAgentChannel.OMNICHANNEL;
        }
        if (mode == null) {
            mode = (script != null && script.mode() != null)
                    ? AiAgentMode.fromScenarioMode(script.mode())
                    : AiAgentMode.SINGLE_PROMPT;
        }
        if (persona == null) {
            persona = AgentPersona.AI_ASSISTANT;
        }
        if (temperature == null) {
            temperature = 0.3;
        }
        if (maxOutputTokens == null) {
            maxOutputTokens = 300;
        }
        // A group is never null: every reader reaches through one, and making them prove
        // it first would put the same null check in every caller instead of here once.
        if (script == null) {
            script = new AiAgentScript(null, null, mode.toScenarioMode(), null, null, null);
        }
        if (speechEngine == null) {
            speechEngine = new AiAgentSpeechEngine(null, null, null, null, null, null, null, null, null);
        }
        if (voice == null) {
            voice = new AiAgentVoice(null, null, null, null, true);
        }
        if (ambience == null) {
            ambience = new AiAgentAmbience(null, null, null, null, null, true, null);
        }
        if (greeting == null) {
            greeting = AgentGreeting.DEFAULT;
        }
        if (callBehaviour == null) {
            callBehaviour = new AiAgentCallBehaviour(null, false, null, null, false, null, null, null, null);
        }
        if (limits == null) {
            limits = AiAgentLimits.NONE;
        }
        if (dataPolicy == null) {
            dataPolicy = new AiAgentDataPolicy(false, true, null, null);
        }
        sipTrunkIds = sipTrunkIds == null ? Set.of() : Set.copyOf(sipTrunkIds);
        dataNeeded = dataNeeded == null ? List.of() : List.copyOf(dataNeeded);
        dataEvaluation = dataEvaluation == null ? List.of() : List.copyOf(dataEvaluation);
        pronunciationRules = pronunciationRules == null ? List.of() : List.copyOf(pronunciationRules);
        postCallActions = postCallActions == null ? List.of() : List.copyOf(postCallActions);
        preCallFunctions = preCallFunctions == null ? List.of() : List.copyOf(preCallFunctions);
        // Enum order, not Set.copyOf's per-JVM order: the snapshot text and its fingerprint
        // have to come out the same for the same rules.
        handbook = handbook == null || handbook.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(handbook));
        // Blank is "not set": the call then runs on the platform's zone (Asia/Tashkent).
        timezone = timezone == null || timezone.isBlank() ? null : timezone.trim();
    }

    /**
     * Whether calls by this agent open with the §11.1 disclosure.
     */
    public boolean disclosureEnabled() {
        return persona == AgentPersona.AI_ASSISTANT;
    }

    /** The voice a call in {@code language} speaks with; this agent's default otherwise. */
    public String voiceFor(String callLanguage) {
        return voice.forLanguage(callLanguage);
    }

    /**
     * The languages a call by this agent may be held in: its own language first, then its
     * other speech languages and every language it has a voice for. Language detection and a mid-call switch stay inside these.
     */
    public List<String> findSpokenLanguages() {
        Set<String> languages = new LinkedHashSet<>(speechLanguages);
        languages.addAll(voice.perLanguage().keySet());
        return List.copyOf(languages);
    }

    /**
     * Whether this agent holds calls in more than one language (Retell's multilingual mode) —
     * judged by {@link #findSpokenLanguages()}, so it says what a call really does: an agent
     * that has a second language's voice switches to it even if the list names one language.
     */
    public boolean multilingual() {
        return findSpokenLanguages().size() > 1;
    }

    public static AiAgentBuilder builder() {
        return new AiAgentBuilder();
    }

    /** A builder that starts from this agent, for changing a few of its fields. */
    public AiAgentBuilder toBuilder() {
        return new AiAgentBuilder(this);
    }

    public AiAgent withFirstMessage(String newFirstMessage) {
        return withScript(script.withFirstMessage(newFirstMessage));
    }

    /**
     * The same agent set to open this one call with exactly {@code line} — a per-contact or
     * webhook override, or the apology after a dropped line. It wins over the greeting's mode
     * and lines; the pause before speaking stays.
     */
    public AiAgent withSpokenOpening(String line) {
        return toBuilder().script(script.withFirstMessage(line)).greeting(greeting.speakingOnlyFirstMessage()).build();
    }

    /**
     * The line this call opens with, or null when the model should compose it. The greeting's
     * own mode decides; whether the agent speaks at all is {@link #speaksFirst()}.
     */
    public String findOpeningLine(String callLanguage, boolean inbound) {
        return greeting.findLine(script.firstMessage(), callLanguage, inbound,
                bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    /** Whether the agent opens the call rather than waiting for the caller. */
    public boolean speaksFirst() {
        return greeting.mode() != GreetingMode.USER_FIRST;
    }

    public AiAgent withScenario(AiAgentScript newScript) {
        return withScript(newScript);
    }

    public AiAgent withChannel(AiAgentChannel newChannel) {
        return toBuilder().channel(newChannel).build();
    }

    public AiAgent withMode(AiAgentMode newMode) {
        return toBuilder()
                .mode(newMode)
                .script(script != null ? new AiAgentScript(script.templateId(), script.scenarioId(), newMode.toScenarioMode(), script.definition(), script.firstMessage(), script.systemPrompt()) : null)
                .build();
    }

    public AiAgent withAnalysis(List<DataExtractionField> newDataNeeded,
                                List<DataEvaluationCriterion> newDataEvaluation) {
        return toBuilder().dataNeeded(newDataNeeded).dataEvaluation(newDataEvaluation).build();
    }

    public AiAgent withLimits(AiAgentLimits newLimits) {
        return toBuilder().limits(newLimits).build();
    }

    /** Everything the "advanced" settings screen owns, in one write. */
    public AiAgent withAdvanced(AiAgentAmbience newAmbience,
                                AiAgentCallBehaviour newCallBehaviour,
                                AiAgentDataPolicy newDataPolicy,
                                AiAgentVoice newVoice,
                                boolean newUseRag,
                                AgentWebhookConfig newInitiationWebhook,
                                AgentWebhookConfig newPostCallWebhook) {
        return toBuilder()
                .ambience(newAmbience)
                .callBehaviour(newCallBehaviour)
                .dataPolicy(newDataPolicy)
                .voice(newVoice)
                .useRag(newUseRag)
                .initiationWebhook(newInitiationWebhook)
                .postCallWebhook(newPostCallWebhook)
                .build();
    }

    public AiAgent withPronunciationRules(List<PronunciationRule> rules) {
        return toBuilder().pronunciationRules(rules).build();
    }

    /** The same configuration as a new, unsaved, never-published agent called {@code newName}. */
    public AiAgent asCopy(String newName, Instant now, Long copiedBy) {
        return toBuilder()
                .id(0L)
                .name(newName)
                .createdAt(now)
                .createdBy(copiedBy)
                .publishedVersion(null)
                .version(0L)
                .build();
    }

    public AiAgent withPublishedVersion(Integer newPublishedVersion) {
        return toBuilder().publishedVersion(newPublishedVersion).build();
    }

    /** The same agent at optimistic-lock {@code newVersion} — a restored snapshot takes the draft row's. */
    public AiAgent withVersion(long newVersion) {
        return toBuilder().version(newVersion).build();
    }

    /** The published snapshot with the draft row's on/off switch — that switch is not versioned. */
    public AiAgent withEnabled(boolean newEnabled) {
        return toBuilder().enabled(newEnabled).build();
    }

    public AiAgent withPostCallActions(List<PostCallAction> actions) {
        return toBuilder().postCallActions(actions).build();
    }

    public AiAgent withPreCallFunctions(List<WorkflowFunction> functions) {
        return toBuilder().preCallFunctions(functions).build();
    }

    private AiAgent withScript(AiAgentScript newScript) {
        return toBuilder().script(newScript).build();
    }
}
