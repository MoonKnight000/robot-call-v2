package uz.murodjon.robotcallv2.dialer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.service.CallContextFactory;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.application.port.input.FactWebhookUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;

/**
 * Everything an AI call needs to know before its number is dialled — the agent, the script's
 * facts, the language and the voice — read off the ARI thread. Used by {@link CallTaskConsumer}
 * and by the predictive dialer, whose clients the agent takes when no operator does.
 */
@Component
public class OutboundCallPreparer {

    private final CrmUseCase crmUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final FactWebhookUseCase factWebhookUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final ObjectMapper objectMapper;

    public OutboundCallPreparer(CrmUseCase crmUseCase, ClientMemoryUseCase clientMemoryUseCase,
                                FactWebhookUseCase factWebhookUseCase, AiAgentUseCase aiAgentUseCase,
                                ObjectMapper objectMapper) {
        this.crmUseCase = crmUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.factWebhookUseCase = factWebhookUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.objectMapper = objectMapper;
    }

    public OutboundCall prepare(CallTask task) {
        // Ahead of everything else, because it decides the script the facts are read
        // against and the voice the call is warmed up for.
        AiAgent agent = aiAgentUseCase.requireCallAgent(task.companyId(), task.aiAgentId(), task.phone());
        Scenario scenario = aiAgentUseCase.resolveScenario(task.companyId(), agent);
        CrmClientSnapshot crm = crmUseCase.fetchClient(task.companyId(), task.clientId());
        ClientMemory memory = clientMemoryUseCase.findByCompanyIdAndPhone(task.companyId(), task.phone());
        CallContext context = CallContextFactory.mergeCrmFacts(
                CallContextFactory.readContextData(objectMapper, task.contextData(), null, scenario.definition().factSchema()), crm)
                .withMemory(memory);
        // Last and freshest. Deliberately here rather than once the call is up: the
        // number has not been dialled yet, so a slow endpoint costs this call's place in
        // the queue and not a silence the person who answered has to sit through.
        if (scenario.definition().factWebhook() != null) {
            context = CallContextFactory.overlayWebhookFacts(objectMapper, context,
                    factWebhookUseCase.fetchFacts(scenario.definition().factWebhook(),
                            FactWebhookRequest.outbound(task.phone(), task.clientId(), task.campaignId())),
                    scenario.definition().factSchema());
        }
        String language = resolveLanguage(task, crm, memory);
        // A variant that named a voice is testing that voice, so it wins over the
        // agent's per-language map outright — otherwise the map would silently pick the
        // agent's voice back up and the variant would differ in name only.
        String voice = task.ttsVoiceOverride() != null && !task.ttsVoiceOverride().isBlank()
                ? task.ttsVoiceOverride()
                : agent.voiceFor(language);

        // The target's own opening line (agentOverrides.firstMessage, NEXT-PLAN A.8).
        String firstMessage = CallContextFactory.readFirstMessageOverride(objectMapper, task.contextData());
        if (firstMessage != null) {
            agent = agent.withSpokenOpening(firstMessage);
        }

        return new OutboundCall(
                task.campaignId(), task.targetId(), task.clientId(), task.phone(),
                language, voice, context, agent, task.companyId(), task.sipTrunkId(),
                task.variantId(), task.promptOverride(), task.pipelineModeOverride(),
                task.callerIdPoolId(), task.localPresence());
    }

    /**
     * Full BCP-47 as the last resort, not a bare "uz": {@code AiAgent.voiceFor} looks the
     * language up in a map keyed the way {@code ai_agent_voice} stores it, so a
     * two-letter tag missed every per-language voice and quietly fell back to the agent's
     * single one.
     */
    private static String resolveLanguage(CallTask task, CrmClientSnapshot crm, ClientMemory memory) {
        return CallLanguage.resolve(
                crm != null ? crm.preferredLanguage() : null,
                memory != null ? memory.preferredLanguage() : null,
                task.language() != null ? task.language() : "uz-UZ");
    }
}
