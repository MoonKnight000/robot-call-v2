package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.crm.application.service.CrmClient;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.mapper.CallContextMapper;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RabbitConfig;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.memory.application.service.ClientMemoryService;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.application.service.FactWebhookClient;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * Processes a single {@link CallTask} dispatched by {@link DialerService} over the
 * dialer's concurrency pool (§10.4).
 */
@Service
public class CallTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(CallTaskConsumer.class);

    private final AriService ariService;
    private final OutboundCallRegistry registry;
    private final CampaignService campaignService;
    private final ScenarioService scenarioService;
    private final CrmClient crmClient;
    private final AuditService audit;
    private final DialerState dialerState;
    private final DoNotCallRepository doNotCallRepository;
    private final CallRecordService callRecordService;
    private final ClientMemoryService clientMemoryService;
    private final FactWebhookClient factWebhookClient;
    private final AiAgentUseCase aiAgents;

    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            CampaignService campaignService, ScenarioService scenarioService,
                            CrmClient crmClient, AuditService audit, DialerState dialerState,
                            DoNotCallRepository doNotCallRepository, CallRecordService callRecordService,
                            ClientMemoryService clientMemoryService, FactWebhookClient factWebhookClient,
                            AiAgentUseCase aiAgents) {
        this.ariService = ariService;
        this.registry = registry;
        this.campaignService = campaignService;
        this.scenarioService = scenarioService;
        this.crmClient = crmClient;
        this.audit = audit;
        this.dialerState = dialerState;
        this.doNotCallRepository = doNotCallRepository;
        this.callRecordService = callRecordService;
        this.clientMemoryService = clientMemoryService;
        this.factWebhookClient = factWebhookClient;
        this.aiAgents = aiAgents;
    }

    @RabbitListener(queues = RabbitConfig.CALL_TASK_QUEUE)
    public void onMessage(CallTask task) {
        process(task, dialerState);
    }

    public void process(CallTask task, DialerState state) {
        // Everything below the DNC guard can fail before Asterisk is ever asked to dial.
        // AriService.originate writes its own row when the originate itself fails, so
        // this flag keeps the two from recording the same attempt twice.
        boolean originateReached = false;
        try {
            // Guard: check DNC one final time before originating
            if (doNotCallRepository.isBlocked(task.companyId(), task.phone())) {
                log.info("Target {} ({}) is in Do-Not-Call list — skipping call origination",
                        task.targetId(), task.phone());
                state.release(task.companyId());
                callRecordService.recordUnplacedAttempt(task.companyId(), task.targetId(), task.phone(),
                        task.language(), Disposition.DO_NOT_CALL, "number is in the do-not-call list");
                campaignService.applyOutcome(task.companyId(), task.targetId(), Disposition.DO_NOT_CALL);
                return;
            }

            // Ahead of everything else, because it decides the script the facts are read
            // against and the voice the call is warmed up for.
            AiAgent agent = aiAgents.requireAgent(task.companyId(), task.aiAgentId());
            Scenario scenario = scenarioService.requireScenario(task.companyId(), agent.scenarioId());
            CrmClientSnapshot crm = crmClient.fetchClient(task.companyId(), task.clientId());
            ClientMemory memory = clientMemoryService.findByCompanyIdAndPhone(task.companyId(), task.phone());
            CallContext context = CallContextMapper.merge(
                    CallContextMapper.fromJson(task.contextData(), null, scenario.definition().factSchema()), crm)
                    .withMemory(memory);
            // Last and freshest. Deliberately here rather than once the call is up: the
            // number has not been dialled yet, so a slow endpoint costs this call's place in
            // the queue and not a silence the person who answered has to sit through.
            context = CallContextMapper.overlayJson(context,
                    factWebhookClient.fetchFacts(scenario.definition().factWebhook(),
                            FactWebhookRequest.outbound(task.phone(), task.clientId(), task.campaignId())),
                    scenario.definition().factSchema());
            String language = resolveLanguage(task, crm, memory);
            // A variant that named a voice is testing that voice, so it wins over the
            // agent's per-language map outright — otherwise the map would silently pick the
            // agent's voice back up and the variant would differ in name only.
            String voice = task.ttsVoiceOverride() != null && !task.ttsVoiceOverride().isBlank()
                    ? task.ttsVoiceOverride()
                    : agent.voiceFor(language);

            OutboundCall outboundCall = new OutboundCall(
                    task.campaignId(), task.targetId(), task.clientId(), task.phone(),
                    language, voice, context, agent, task.companyId(), task.sipTrunkId(),
                    task.variantId(), task.promptOverride());

            // Originate with atomic registry pre-arming to eliminate StasisStart race condition
            originateReached = true;
            String channelId = ariService.originate(task.phone(), task.companyId(), outboundCall);

            audit.record(task.companyId(), "CALL_ORIGINATE", "call", channelId,
                    "target " + task.targetId() + " -> " + task.phone() + " (" + language + ")");
            log.info("Originated target {} -> channel {} (lang={}, trunkId={})",
                    task.targetId(), channelId, language, task.sipTrunkId());
        } catch (Exception e) {
            log.warn("Originate failed for target {} ({}): {}", task.targetId(), task.phone(), e.getMessage());
            state.release(task.companyId());
            if (!originateReached) {
                callRecordService.recordUnplacedAttempt(task.companyId(), task.targetId(), task.phone(),
                        task.language(), Disposition.FAILED, "call preparation failed: " + e.getMessage());
            }
            campaignService.applyOutcome(task.companyId(), task.targetId(), Disposition.FAILED);
        }
    }

    /** CRM first, then what an operator noted on the client, then the target/campaign default. */
    private static String resolveLanguage(CallTask task, CrmClientSnapshot crm, ClientMemory memory) {
        if (crm != null && crm.preferredLanguage() != null && !crm.preferredLanguage().isBlank()) {
            return crm.preferredLanguage();
        }
        if (memory != null && memory.preferredLanguage() != null && !memory.preferredLanguage().isBlank()) {
            return memory.preferredLanguage();
        }
        return task.language() != null ? task.language() : "uz";
    }
}
