package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
 * over real recordings; this half is about what the agent <em>decides</em>. The call itself
 * is played by {@link AgentSimulator}, which agent evaluations (NEXT-PLAN A.2) share.
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConfigurableApplicationContext context;
    private final AgentSimulator simulator;
    private final ScenarioUseCase scenarioUseCase;
    private final CompanyProperties companyProperties;
    private final String personasPath;
    private final long scenarioId;
    private final String language;
    private final int maxTurns;
    private final String personaModel;

    public DialogSimulationRunner(ConfigurableApplicationContext context,
                                  AgentSimulator simulator,
                                  ScenarioUseCase scenarioUseCase,
                                  CompanyProperties companyProperties,
                                  SimulationProperties simulationProperties) {
        this.context = context;
        this.simulator = simulator;
        this.scenarioUseCase = scenarioUseCase;
        this.companyProperties = companyProperties;
        this.personasPath = simulationProperties.personasPath();
        this.scenarioId = simulationProperties.scenarioId();
        this.language = simulationProperties.language();
        this.maxTurns = simulationProperties.maxTurns();
        this.personaModel = simulationProperties.personaModel();
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
        if (!simulator.available()) {
            log.error("Simulation needs an LLM ChatModel (set GEMINI_API_KEY or OPENAI_API_KEY)");
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
            scenario = scenarioUseCase.requireScenario(companyProperties.defaultId(), scenarioId).definition();
        } catch (Exception e) {
            log.error("Simulation could not start: {}", e.getMessage());
            return 1;
        }
        if (personas.isEmpty()) {
            log.error("Simulation loaded no personas from {}", personasPath);
            return 1;
        }

        int failures = 0;
        log.info("=== SIMULATION: {} persona(s), scenario {} ===", personas.size(), scenarioId);
        for (SimulationPersona persona : personas) {
            failures += simulate(persona, scenario) ? 0 : 1;
        }
        log.info("=== SIMULATION: {}/{} passed ===", personas.size() - failures, personas.size());
        return failures > 0 ? 1 : 0;
    }

    /** @return whether this persona's expectations held */
    private boolean simulate(SimulationPersona persona, ScenarioDefinition scenario) {
        log.info("--- persona: {} ---", persona.id());
        SimulatedCall call = simulator.simulate(companyProperties.defaultId(), scenario, null, language,
                persona, maxTurns, personaModel);
        call.transcript().forEach(line -> log.info("{}", line));
        List<String> problems = new ArrayList<>(call.problems());
        checkExpectations(persona, call, problems);
        if (problems.isEmpty()) {
            log.info("PASS {} (disposition={}, stage={})", persona.id(), call.disposition(), call.stage());
            return true;
        }
        log.warn("FAIL {} (disposition={}, stage={}): {}",
                persona.id(), call.disposition(), call.stage(), String.join("; ", problems));
        return false;
    }

    /** What this persona was supposed to end up with, against what it did. */
    private static void checkExpectations(SimulationPersona persona, SimulatedCall call, List<String> problems) {
        String disposition = call.disposition() == null ? null : call.disposition().name();
        if (persona.expectDisposition() != null && !persona.expectDisposition().equalsIgnoreCase(disposition)) {
            problems.add("expected disposition " + persona.expectDisposition() + ", got " + disposition);
        }
        if (persona.expectStage() != null && !persona.expectStage().equalsIgnoreCase(call.stage())) {
            problems.add("expected stage " + persona.expectStage() + ", got " + call.stage());
        }
    }
}
