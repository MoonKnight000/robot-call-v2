package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceSettingsUseCase;

import java.util.ArrayList;
import java.util.List;

/**
 * One scripted caller against the real conversation stack — the core of
 * {@link DialogSimulationRunner} and of agent evaluations (NEXT-PLAN A.2).
 *
 * <p>The agent side is built and run the way {@link TurnRunner} builds a turn: the same
 * prompt prefix, the same per-turn annex, the same stage-scoped tool set, the same fact
 * guard over the line before it counts as spoken. The caller side is a second model
 * playing a persona — one model playing both sides agrees with itself far too readily.
 *
 * <p>Deliberately left out: audio (no RTP, recognizer, synthesis or barge-in) and every
 * tool that reaches outside the call. The session carries no company REST tools and an
 * empty MCP tool set, so a simulated call can never create a lead or write a note in a
 * real CRM; the scenario's own tools — recording an outcome, ending the call — still run.
 */
@Component
public class AgentSimulator {

    /** What the agent is told to open with — the same bootstrap a real greeting turn uses. */
    private static final String GREETING_BOOTSTRAP =
            "[TIZIM: Qo'ng'iroq ulandi, mijoz go'shakni ko'tardi. Rejaga muvofiq salomlashing.]";

    private static final String PERSONA_RULES = """
            Siz telefon qo'ng'irog'iga javob bergan odamsiz — operator emas, mijozsiz.
            Faqat mijoz sifatida gapiring, hech qachon agent rolini o'ynamang.
            Javoblaringiz og'zaki nutqdek qisqa bo'lsin: bir-ikki jumla, ro'yxat yo'q.
            Suhbat tugadi deb hisoblasangiz, oddiy odamdek xayrlashing.
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final TextTurnEngine textTurnEngine;
    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final VoiceSettingsUseCase voiceSettingsUseCase;

    public AgentSimulator(ObjectProvider<ChatModel> chatModelProvider, TextTurnEngine textTurnEngine,
                          AiModelConfigUseCase aiModelConfigUseCase,
                          VoiceSettingsUseCase voiceSettingsUseCase) {
        this.chatModelProvider = chatModelProvider;
        this.textTurnEngine = textTurnEngine;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.voiceSettingsUseCase = voiceSettingsUseCase;
    }

    /** Whether there is a model to play either side with. */
    public boolean available() {
        return chatModelProvider.getIfAvailable() != null;
    }

    /**
     * One whole call. The agent speaks first — its own {@code firstMessage} when it has
     * one, as a real call does — and the two sides alternate until a tool ends the call or
     * {@code maxTurns} is reached.
     *
     * @param agent the agent (a draft or a published snapshot) whose model settings and
     *              opening line the call uses; null for a bare scenario run
     */
    public SimulatedCall simulate(long companyId, ScenarioDefinition scenario, AiAgent agent, String language,
                                  SimulationPersona persona, int maxTurns, String personaModel) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return SimulatedCall.failed("no LLM ChatModel is configured (GEMINI_API_KEY or OPENAI_API_KEY)");
        }
        ChatClient agentClient = TurnTools.chatClient(chatModel);
        ChatClient callerClient = ChatClient.create(chatModel);
        DialogSession session = newSession(companyId, persona, scenario, agent, language);
        List<String> transcript = new ArrayList<>();
        List<Message> callerHistory = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String opening = agent != null ? agent.script().firstMessage() : null;
        String clientText = GREETING_BOOTSTRAP;

        for (int turn = 1; turn <= maxTurns && !session.isEnded(); turn++) {
            String agentLine;
            if (turn == 1 && opening != null && !opening.isBlank()) {
                agentLine = opening.trim();
                session.history().add(new AssistantMessage(agentLine));
                session.setLastAgentText(agentLine);
            } else {
                agentLine = agentTurn(agentClient, session, clientText, problems);
            }
            if (agentLine == null || agentLine.isBlank()) {
                problems.add("turn " + turn + ": the agent said nothing");
                break;
            }
            transcript.add("AGENT: " + agentLine);
            if (session.isEnded()) {
                break;
            }
            callerHistory.add(new AssistantMessage(agentLine));
            clientText = callerTurn(callerClient, persona, callerHistory, personaModel);
            if (clientText == null || clientText.isBlank()) {
                problems.add("turn " + turn + ": the caller said nothing");
                break;
            }
            transcript.add("CLIENT: " + clientText);
            callerHistory.add(new UserMessage(clientText));
        }
        return new SimulatedCall(transcript,
                GoalOutcome.settle(session.disposition(), session.scenario(), session.outcome()),
                session.state(), problems);
    }

    /**
     * One agent turn — {@link TextTurnEngine}, the same turn a chat message gets and the
     * same one {@link TurnRunner} runs on a call. Only the fact guard's verdict is handled
     * differently here: reported rather than swallowed, because a figure that is not in the
     * file is the one failure this whole system exists to prevent (§4.4), and an evaluation
     * exists to fail on it.
     */
    private String agentTurn(ChatClient agent, DialogSession session, String clientText, List<String> problems) {
        TextTurn turn = textTurnEngine.reply(agent, session, clientText);
        if (!turn.violations().isEmpty()) {
            problems.add("fact guard: figures " + turn.violations() + " are not in the call facts");
        }
        return turn.line();
    }

    /** One caller turn: the persona answers whatever the agent just said. */
    private String callerTurn(ChatClient caller, SimulationPersona persona, List<Message> callerHistory,
                              String personaModel) {
        ChatModel cm = chatModelProvider.getIfAvailable();
        boolean isOpenAi = (cm instanceof OpenAiChatModel)
                || (personaModel != null && (personaModel.startsWith("llama") || personaModel.startsWith("mixtral")
                || personaModel.startsWith("gpt-") || personaModel.startsWith("qwen")));
        ChatOptions options;
        if (isOpenAi) {
            options = OpenAiChatOptions.builder()
                    .model(personaModel)
                    .maxTokens(200)
                    .build();
        } else {
            options = GoogleGenAiChatOptions.builder()
                    .model(personaModel)
                    .maxOutputTokens(200)
                    .thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                    .build();
        }
        ChatResponse response = caller.prompt()
                .system(PERSONA_RULES + "\nSIZNING ROLINGIZ:\n" + persona.prompt())
                .messages(callerHistory)
                .options(options.mutate())
                .call()
                .chatResponse();
        return textOf(response);
    }

    /**
     * A simulated call: real scenario, real facts, the agent's model settings — and no
     * telephony, no company REST tools and no MCP tools.
     */
    private DialogSession newSession(long companyId, SimulationPersona persona, ScenarioDefinition scenario,
                                     AiAgent agent, String language) {
        EffectiveAiModelConfig aiModel = aiModelConfigUseCase.findEffectiveByCompanyId(companyId);
        if (agent != null) {
            aiModel = aiModel.withOverrides(agent.llmModel(), agent.temperature(), agent.maxOutputTokens());
        }
        // No endpoint, no watchdog and no hangup: nothing here plays audio or ends a
        // channel, and every path that would touch one is guarded against null.
        DialogSession session = DialogSession.builder("sim-" + persona.id(), language)
                .context(new CallContext(persona.facts(), null))
                .scenario(scenario)
                .companyId(companyId)
                .disclosureEnabled(false)
                .aiModel(aiModel)
                .voiceSettings(voiceSettingsUseCase.findEffectiveByCompanyId(companyId))
                .build();
        session.setMcpTools(List.of());
        return session;
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}
