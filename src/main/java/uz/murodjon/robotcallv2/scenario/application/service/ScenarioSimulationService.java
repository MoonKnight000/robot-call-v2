package uz.murodjon.robotcallv2.scenario.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioSimulationRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioSimulationResponse;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.PersonaTestResult;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides text-based and AI-vs-AI automated test simulation for Scenarios.
 */
@Service
public class ScenarioSimulationService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioSimulationService.class);

    private final ScenarioUseCase scenarioService;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public ScenarioSimulationService(ScenarioUseCase scenarioService,
                                     ObjectProvider<ChatModel> chatModelProvider) {
        this.scenarioService = scenarioService;
        this.chatModelProvider = chatModelProvider;
    }

    public ScenarioSimulationResponse simulateTurn(ScenarioSimulationRequest request) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return new ScenarioSimulationResponse(
                    "Simulyatsiya rejimi: GEMINI_API_KEY sozlanmaganligi sababli test javobi qaytarildi.",
                    request.currentState() != null ? request.currentState() : "GREETING",
                    null,
                    Map.of(),
                    Map.of(),
                    null,
                    false,
                    10
            );
        }

        ScenarioDefinition definition = request.scenarioDefinition();
        if (definition == null && request.scenarioId() != null) {
            Scenario s = scenarioService.requireScenario(request.scenarioId());
            definition = s.definition();
        }
        if (definition == null) {
            throw new ValidationException(null, "Scenario definition must be provided");
        }

        String defaultInitialStage = definition.stages() != null && !definition.stages().isEmpty()
                ? definition.stages().get(0).id()
                : "GREETING";

        String currentState = request.currentState() != null && !request.currentState().isBlank()
                ? request.currentState()
                : defaultInitialStage;

        long start = System.currentTimeMillis();

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("Siz ovozli AI agent rolidasiz. Ssenariy bo'yicha tabiiy suhbat olib boring.\n");
        systemPrompt.append("ROL VA VAZIFA:\n").append(definition.rolePrompt()).append("\n\n");
        systemPrompt.append("JORIY BOSQICH: ").append(currentState).append("\n");

        if (request.contextFacts() != null && !request.contextFacts().isEmpty()) {
            systemPrompt.append("FAKTLAR:\n");
            request.contextFacts().forEach((k, v) -> systemPrompt.append("- ").append(k).append(": ").append(v).append("\n"));
            systemPrompt.append("\n");
        }

        systemPrompt.append("QOIDALAR:\n");
        if (definition.guardrails() != null) {
            for (String g : definition.guardrails()) {
                systemPrompt.append("- ").append(g).append("\n");
            }
        }
        systemPrompt.append("- Mijozga insondek samimiy va qisqa (1-2 gap) javob bering.\n");
        systemPrompt.append("- Agar bosqich o'zgarsa yoki va'da/rad qayd etilsa, javob oxirida maxsus JSON teg qo'shing: [STATE: yangi_bosqich] yoki [OUTCOME: outcome_nomi].\n");

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt.toString()));

        if (request.chatHistory() != null) {
            for (Map<String, String> entry : request.chatHistory()) {
                String role = entry.get("role");
                String content = entry.get("content");
                if ("assistant".equalsIgnoreCase(role)) {
                    messages.add(new AssistantMessage(content));
                } else if ("user".equalsIgnoreCase(role)) {
                    messages.add(new UserMessage(content));
                }
            }
        }

        if (request.userMessage() != null && !request.userMessage().isBlank()) {
            messages.add(new UserMessage(request.userMessage()));
        } else if (request.chatHistory() == null || request.chatHistory().isEmpty()) {
            messages.add(new UserMessage("[Qo'ng'iroq ulandi, mijoz go'shakni ko'tardi. Salomlashing.]"));
        }

        ChatClient chatClient = ChatClient.create(chatModel);
        ChatResponse resp = chatClient.prompt()
                .messages(messages)
                .call()
                .chatResponse();

        long latencyMs = System.currentTimeMillis() - start;
        String rawReply = resp.getResult() != null && resp.getResult().getOutput() != null
                ? resp.getResult().getOutput().getText() : "";

        String cleanReply = rawReply;
        String nextState = currentState;
        String disposition = null;
        boolean ended = false;

        if (cleanReply.contains("[STATE:")) {
            int idx = cleanReply.indexOf("[STATE:");
            int endIdx = cleanReply.indexOf("]", idx);
            if (endIdx > idx) {
                nextState = cleanReply.substring(idx + 7, endIdx).trim();
                cleanReply = cleanReply.substring(0, idx) + cleanReply.substring(endIdx + 1);
            }
        }

        if (cleanReply.contains("[OUTCOME:")) {
            int idx = cleanReply.indexOf("[OUTCOME:");
            int endIdx = cleanReply.indexOf("]", idx);
            if (endIdx > idx) {
                disposition = cleanReply.substring(idx + 9, endIdx).trim();
                cleanReply = cleanReply.substring(0, idx) + cleanReply.substring(endIdx + 1);
                ended = true;
            }
        }

        return new ScenarioSimulationResponse(
                cleanReply.trim(),
                nextState,
                null,
                Map.of(),
                Map.of(),
                disposition,
                ended,
                latencyMs
        );
    }

    public List<PersonaTestResult> runPersonaTests(Long scenarioId) {
        Scenario scenario = scenarioService.requireScenario(scenarioId);
        ScenarioDefinition def = scenario.definition();

        List<PersonaTestResult> results = new ArrayList<>();

        results.add(runSinglePersonaTest(def, "Ijobiy mijoz (To'lovga rozi)",
                "Siz qarzdorsiz. Bot qo'ng'iroq qilganda xushmuomala bo'ling va juma kuni to'lashga va'da bering.",
                List.of("Alo, eshitaman", "Ha, o'ziman", "To'g'ri, esimdan chiqibdi. Juma kunigacha to'lab beraman.", "Rahmat, kelishdik.")));

        results.add(runSinglePersonaTest(def, "Qiyin vaziyatdagi mijoz (Oylik kechikdi)",
                "Siz qarzdorsiz, lekin oylik kechikkani sababli hozir pulingiz yo'q. Faqat keyingi oyning boshida to'lay olasiz.",
                List.of("Labbay, kim bu?", "Ha, qanaqa qarz?", "Hozir umuman pulim yo'q, oylik kechikkan. Keyingi oyning 5-sanasida to'lasam maylimi?", "Xo'p, rahmat.")));

        results.add(runSinglePersonaTest(def, "Adashgan raqam (Boshqa odam)",
                "Siz bu raqamning yangi egasisiz va so'ralgan odam emassiz. Adashganini aniq ayting.",
                List.of("Alo?", "Kechirasiz, men u odam emasman, raqamni adashtirdingiz.", "Yo'q, bunaqa odamni tanimayman. Boshqa qo'ng'iroq qilmang.")));

        return results;
    }

    private PersonaTestResult runSinglePersonaTest(ScenarioDefinition def, String name, String personaPrompt, List<String> clientTurns) {
        List<Map<String, String>> transcript = new ArrayList<>();
        String currentState = def.stages() != null && !def.stages().isEmpty()
                ? def.stages().get(0).id()
                : "GREETING";
        String disposition = "IN_PROGRESS";
        boolean passed = true;

        try {
            ScenarioSimulationResponse botResp = simulateTurn(new ScenarioSimulationRequest(
                    null, def, null, currentState, transcript,
                    Map.of("clientName", "Azizbek", "debtAmount", "1,200,000 so'm", "dueDate", "2026-05-01"),
                    "uz"
            ));

            transcript.add(Map.of("role", "assistant", "content", botResp.assistantReply()));
            currentState = botResp.nextState();

            for (String turnText : clientTurns) {
                transcript.add(Map.of("role", "user", "content", turnText));

                botResp = simulateTurn(new ScenarioSimulationRequest(
                        null, def, turnText, currentState, transcript,
                        Map.of("clientName", "Azizbek", "debtAmount", "1,200,000 so'm", "dueDate", "2026-05-01"),
                        "uz"
                ));

                transcript.add(Map.of("role", "assistant", "content", botResp.assistantReply()));
                currentState = botResp.nextState();
                if (botResp.disposition() != null) {
                    disposition = botResp.disposition();
                }
                if (botResp.ended()) {
                    break;
                }
            }

            if (disposition.equals("IN_PROGRESS")) {
                disposition = "COMPLETED";
            }

            return new PersonaTestResult(name, personaPrompt, passed, disposition, transcript, Map.of("finalState", currentState), null);
        } catch (Exception e) {
            log.warn("Persona test failed for {}: {}", name, e.getMessage());
            return new PersonaTestResult(name, personaPrompt, false, "ERROR", transcript, Map.of(), e.getMessage());
        }
    }
}
