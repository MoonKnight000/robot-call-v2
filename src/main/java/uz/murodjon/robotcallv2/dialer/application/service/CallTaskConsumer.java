package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
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
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
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

    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            CampaignService campaignService, ScenarioService scenarioService,
                            CrmClient crmClient, AuditService audit, DialerState dialerState,
                            DoNotCallRepository doNotCallRepository, CallRecordService callRecordService) {
        this.ariService = ariService;
        this.registry = registry;
        this.campaignService = campaignService;
        this.scenarioService = scenarioService;
        this.crmClient = crmClient;
        this.audit = audit;
        this.dialerState = dialerState;
        this.doNotCallRepository = doNotCallRepository;
        this.callRecordService = callRecordService;
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
                campaignService.applyOutcome(task.targetId(), Disposition.DO_NOT_CALL);
                return;
            }

            Scenario scenario = scenarioService.requireScenario(task.scenarioId());
            CrmClientSnapshot crm = crmClient.fetchClient(task.companyId(), task.clientId());
            CallContext context = CallContextMapper.merge(
                    CallContextMapper.fromJson(task.contextData(), null, scenario.definition().factSchema()), crm);
            String language = resolveLanguage(task, crm);
            String voice = task.voiceFor(language);

            OutboundCall outboundCall = new OutboundCall(
                    task.campaignId(), task.targetId(), task.clientId(), task.phone(),
                    language, voice, context, task.scenarioId(), task.companyId(), task.disclosureEnabled(),
                    task.ambientSound(), task.midCallSmsEnabled(), task.midCallSmsTemplate(),
                    task.voicemailAction(), task.voicemailMessage(), task.dtmfInputEnabled(),
                    task.emotionAdaptiveVoice(), task.agentPersona(), task.languageVoices(), task.sipTrunkId());

            // Originate with atomic registry pre-arming to eliminate StasisStart race condition
            originateReached = true;
            String channelId = ariService.originate(task.phone(), task.companyId(), outboundCall);

            audit.record("CALL_ORIGINATE", "call", channelId,
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
            campaignService.applyOutcome(task.targetId(), Disposition.FAILED);
        }
    }

    private static String resolveLanguage(CallTask task, CrmClientSnapshot crm) {
        if (crm != null && crm.preferredLanguage() != null && !crm.preferredLanguage().isBlank()) {
            return crm.preferredLanguage();
        }
        return task.language() != null ? task.language() : "uz";
    }
}
