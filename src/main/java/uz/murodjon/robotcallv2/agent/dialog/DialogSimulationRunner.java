package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aimodel.application.service.AiModelConfigService;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.voice.application.service.VoiceSettingsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs scripted callers against the real conversation stack and reports what each one got.
 *
 * <p><b>Why.</b> Every change to the prompt, the model, the tool set or the scenario can
 * break a conversation in a way no unit test sees and no compiler catches — the agent stops
 * recording refusals, starts answering an Uzbek caller in Russian, forgets to ask for the
 * date. Today the only way to find that out is to dial a real number and listen. This dials
 * twenty of them at once, in a second each, with the same prompt, the same tools and the
 * same guardrails a real call uses.
 *
 * <p><b>What it does not cover</b>, deliberately: audio. No RTP, no recognizer, no
 * synthesis, no barge-in, no endpointing — a persona's turn arrives as clean text, which a
 * caller's never does. {@code TurnReplayTool} is the other half of the picture and works
 * over real recordings; this half is about what the agent <em>decides</em>.
 *
 * <p>Two models, on purpose: the agent's and the caller's. One model playing both sides
 * agrees with itself far too readily, and a suite that always passes is worse than none.
 *
 * <p>Runs at startup when {@code voice-agent.simulation.enabled=true} and then <b>stops the
 * application</b>, exiting {@code 1} if any persona failed. It spends tokens on every boot,
 * so it is a deliberate run and never a deployment setting — and a run that ends in an exit
 * code is one CI can gate on, which a log line saying "8/10 passed" is not.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.simulation", name = "enabled", havingValue = "true")
public class DialogSimulationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DialogSimulationRunner.class);

    /** What the agent is told to open with — the same bootstrap a real greeting turn uses. */
    private static final String GREETING_BOOTSTRAP =
            "[TIZIM: Qo'ng'iroq ulandi, mijoz go'shakni ko'tardi. Rejaga muvofiq salomlashing.]";

    private static final String PERSONA_RULES = """
            Siz telefon qo'ng'irog'iga javob bergan odamsiz — operator emas, mijozsiz.
            Faqat mijoz sifatida gapiring, hech qachon agent rolini o'ynamang.
            Javoblaringiz og'zaki nutqdek qisqa bo'lsin: bir-ikki jumla, ro'yxat yo'q.
            Suhbat tugadi deb hisoblasangiz, oddiy odamdek xayrlashing.
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConfigurableApplicationContext context;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final SystemPromptFactory promptFactory;
    private final TurnTools turnTools;
    private final ScenarioService scenarioService;
    private final AiModelConfigService aiModelConfigService;
    private final VoiceSettingsService voiceSettingsService;
    private final CompanyProperties companyProperties;
    private final String personasPath;
    private final long scenarioId;
    private final String language;
    private final int maxTurns;
    private final String personaModel;

    public DialogSimulationRunner(ConfigurableApplicationContext context,
                                  ObjectProvider<ChatModel> chatModelProvider,
                                  SystemPromptFactory promptFactory,
                                  TurnTools turnTools,
                                  ScenarioService scenarioService,
                                  AiModelConfigService aiModelConfigService,
                                  VoiceSettingsService voiceSettingsService,
                                  CompanyProperties companyProperties,
                                  @Value("${voice-agent.simulation.personas-path:}") String personasPath,
                                  @Value("${voice-agent.simulation.scenario-id:0}") long scenarioId,
                                  @Value("${voice-agent.simulation.language:uz-UZ}") String language,
                                  @Value("${voice-agent.simulation.max-turns:12}") int maxTurns,
                                  @Value("${voice-agent.simulation.persona-model:gemini-3.8-flash}") String personaModel) {
        this.context = context;
        this.chatModelProvider = chatModelProvider;
        this.promptFactory = promptFactory;
        this.turnTools = turnTools;
        this.scenarioService = scenarioService;
        this.aiModelConfigService = aiModelConfigService;
        this.voiceSettingsService = voiceSettingsService;
        this.companyProperties = companyProperties;
        this.personasPath = personasPath;
        this.scenarioId = scenarioId;
        this.language = language;
        this.maxTurns = maxTurns;
        this.personaModel = personaModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = simulateAll();
        // Stop here either way. A run that could not start is not a pass, and leaving the
        // application up afterwards would have a CI job waiting on a process that never
        // ends — and, locally, a bootRun quietly serving traffic on a simulation profile.
        log.info("Simulation finished; shutting down with exit code {}", exitCode);
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** @return the process exit code: 0 when every persona met its expectations. */
    private int simulateAll() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.error("Simulation needs an LLM ChatModel (set GEMINI_API_KEY)");
            return 1;
        }
        if (personasPath == null || personasPath.isBlank() || scenarioId <= 0) {
            log.error("Simulation needs voice-agent.simulation.personas-path and .scenario-id");
            return 1;
        }
        List<SimulationPersona> personas;
        ScenarioDefinition scenario;
        try {
            personas = List.of(MAPPER.readValue(Files.readString(Path.of(personasPath)),
                    SimulationPersona[].class));
            scenario = scenarioService.requireScenario(scenarioId).definition();
        } catch (Exception e) {
            log.error("Simulation could not start: {}", e.getMessage());
            return 1;
        }
        if (personas.isEmpty()) {
            log.error("Simulation loaded no personas from {}", personasPath);
            return 1;
        }

        ChatClient agent = ChatClient.create(chatModel);
        ChatClient caller = ChatClient.create(chatModel);
        int failures = 0;
        log.info("=== SIMULATION: {} persona(s), scenario {} ===", personas.size(), scenarioId);
        for (SimulationPersona persona : personas) {
            failures += simulate(agent, caller, persona, scenario) ? 0 : 1;
        }
        log.info("=== SIMULATION: {}/{} passed ===", personas.size() - failures, personas.size());
        return failures > 0 ? 1 : 0;
    }

    /**
     * One whole call. The agent speaks first, exactly as it does when a channel answers,
     * and the two sides alternate until a tool ends the call or the turn cap is reached.
     *
     * @return whether this persona's expectations held
     */
    private boolean simulate(ChatClient agent, ChatClient caller, SimulationPersona persona,
                             ScenarioDefinition scenario) {
        DialogSession session = newSession(persona, scenario);
        List<Message> callerHistory = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String clientText = GREETING_BOOTSTRAP;

        log.info("--- persona: {} ---", persona.id());
        for (int turn = 1; turn <= maxTurns && !session.isEnded(); turn++) {
            String agentLine = agentTurn(agent, session, clientText, problems);
            if (agentLine == null || agentLine.isBlank()) {
                problems.add("turn " + turn + ": the agent said nothing");
                break;
            }
            log.info("AGENT: {}", agentLine);
            if (session.isEnded()) {
                break;
            }
            callerHistory.add(new AssistantMessage(agentLine));
            clientText = callerTurn(caller, persona, callerHistory);
            if (clientText == null || clientText.isBlank()) {
                problems.add("turn " + turn + ": the caller said nothing");
                break;
            }
            log.info("CLIENT: {}", clientText);
            callerHistory.add(new UserMessage(clientText));
        }

        checkExpectations(persona, session, problems);
        if (problems.isEmpty()) {
            log.info("PASS {} (disposition={}, stage={})", persona.id(), session.disposition(), session.state());
            return true;
        }
        log.warn("FAIL {} (disposition={}, stage={}): {}",
                persona.id(), session.disposition(), session.state(), String.join("; ", problems));
        return false;
    }

    /**
     * One agent turn, built and executed the way {@link TurnRunner} builds and executes it
     * — the same prompt prefix, the same per-turn annex, the same stage-scoped tool set,
     * and the same fact guard over the line before it counts as spoken. Only the speech is
     * missing.
     */
    private String agentTurn(ChatClient agent, DialogSession session, String clientText, List<String> problems) {
        session.history().add(new UserMessage(clientText));
        List<Message> messages = new ArrayList<>(session.history());
        messages.add(new UserMessage(promptFactory.turnAnnex(session)));
        session.clearToolReplies();

        List<ToolCallback> tools = turnTools.build(session);
        ChatResponse response = agent.prompt()
                .system(systemPrefix(session))
                .messages(messages)
                .options(turnTools.buildOptions(session, tools))
                .call()
                .chatResponse();

        turnTools.run(session, tools, turnTools.extractCalls(response));
        String line = textOf(response);
        if (line == null || line.isBlank()) {
            line = session.toolReplies(); // a tool-only turn carries its line in the tool call
        }
        if (line != null && !line.isBlank()) {
            List<String> bad = FactGuard.violations(line, session.scenario(), session.context());
            if (!bad.isEmpty()) {
                // The same check the caller's audio would have been stopped by. Reported
                // rather than swallowed: a figure that is not in the file is the one
                // failure this whole system exists to prevent (§4.4).
                problems.add("fact guard: figures " + bad + " are not in the call facts");
            }
            session.history().add(new AssistantMessage(line));
            session.setLastAgentText(line);
        }
        return line;
    }

    /** One caller turn: the persona answers whatever the agent just said. */
    private String callerTurn(ChatClient caller, SimulationPersona persona, List<Message> callerHistory) {
        ChatResponse response = caller.prompt()
                .system(PERSONA_RULES + "\nSIZNING ROLINGIZ:\n" + persona.prompt())
                .messages(callerHistory)
                .options(GoogleGenAiChatOptions.builder()
                        .model(personaModel)
                        .maxOutputTokens(200)
                        .thinkingLevel(GoogleGenAiThinkingLevel.LOW)
                        .build())
                .call()
                .chatResponse();
        return textOf(response);
    }

    /** What this persona was supposed to end up with, against what it did. */
    private static void checkExpectations(SimulationPersona persona, DialogSession session, List<String> problems) {
        String disposition = session.disposition() == null ? null : session.disposition().name();
        if (persona.expectDisposition() != null && !persona.expectDisposition().equalsIgnoreCase(disposition)) {
            problems.add("expected disposition " + persona.expectDisposition() + ", got " + disposition);
        }
        if (persona.expectStage() != null && !persona.expectStage().equalsIgnoreCase(session.state())) {
            problems.add("expected stage " + persona.expectStage() + ", got " + session.state());
        }
    }

    /** A simulated call: real scenario, real facts, real settings — and no telephony. */
    private DialogSession newSession(SimulationPersona persona, ScenarioDefinition scenario) {
        long companyId = companyProperties.defaultId();
        // No endpoint, no watchdog and no hangup: nothing here plays audio or ends a
        // channel, and every path that would touch one is guarded against null.
        return new DialogSession("sim-" + persona.id(), language, null,
                new CallContext(persona.facts(), null), scenario, null, null, null, 0L, null, false,
                null, null, aiModelConfigService.findEffectiveByCompanyId(companyId),
                voiceSettingsService.effective(companyId));
    }

    /** The prompt prefix, built once per simulated call exactly as a real one caches it. */
    private String systemPrefix(DialogSession session) {
        String prefix = session.systemPrefix();
        if (prefix == null) {
            prefix = promptFactory.stablePrefix(session);
            session.setSystemPrefix(prefix);
        }
        return prefix;
    }

    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}
