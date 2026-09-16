package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.service.CallContextFactory;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.application.port.input.FactWebhookUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Instant;

/**
 * Processes a single {@link CallTask} dispatched by {@link DialerService} over the
 * dialer's concurrency pool (§10.4).
 */
@Service
public class CallTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(CallTaskConsumer.class);

    private final AriService ariService;
    private final OutboundCallRegistry registry;
    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CrmUseCase crmUseCase;
    private final AuditUseCase audit;
    private final DialerState dialerState;
    private final DoNotCallUseCase doNotCallUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final FactWebhookUseCase factWebhookUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final CallBillingUseCase callBilling;
    /** Whose dial window the task is re-checked against at the last moment. */
    private final DialerService dialerService;
    private final CompanyUseCase companyUseCase;

    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                            CrmUseCase crmUseCase, AuditUseCase audit, DialerState dialerState,
                            DoNotCallUseCase doNotCallUseCase, CallAttemptUseCase callAttemptUseCase,
                            ClientMemoryUseCase clientMemoryUseCase, FactWebhookUseCase factWebhookUseCase,
                            AiAgentUseCase aiAgentUseCase, CallBillingUseCase callBilling, DialerService dialerService,
                            CompanyUseCase companyUseCase) {
        this.dialerService = dialerService;
        this.companyUseCase = companyUseCase;
        this.ariService = ariService;
        this.registry = registry;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.crmUseCase = crmUseCase;
        this.audit = audit;
        this.dialerState = dialerState;
        this.doNotCallUseCase = doNotCallUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.factWebhookUseCase = factWebhookUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callBilling = callBilling;
    }

    @RabbitListener(queues = CallTask.QUEUE)
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
            if (doNotCallUseCase.contains(task.companyId(), task.phone())) {
                log.info("Target {} ({}) is in Do-Not-Call list — skipping call origination",
                        task.targetId(), PhoneMasker.mask(task.phone()));
                state.release(task.companyId(), task.campaignId());
                callBilling.releaseReservation(task.companyId(), task.targetId());
                callAttemptUseCase.recordUnplacedAttempt(task.companyId(), task.targetId(), task.phone(),
                        task.language(), Disposition.DO_NOT_CALL, "number is in the do-not-call list");
                campaignTargetUseCase.applyOutcome(task.companyId(), task.targetId(), Disposition.DO_NOT_CALL);
                return;
            }

            // The dialer only claims for active companies, but a task queued before a suspension
            // would still be dialled without this.
            Company company = companyUseCase.findById(task.companyId());
            if (company != null && company.status() == CompanyStatus.SUSPENDED) {
                log.info("Target {} ({}) not dialled: company {} is suspended", task.targetId(),
                        PhoneMasker.mask(task.phone()), task.companyId());
                state.release(task.companyId(), task.campaignId());
                callBilling.releaseReservation(task.companyId(), task.targetId());
                campaignTargetUseCase.releaseClaim(task.targetId(), Instant.now());
                return;
            }

            // Re-read at the last moment: the task may have waited in the queue while the
            // campaign was paused or finished, or while its dial window closed. Nothing is
            // dialled, so the claim goes back — the attempt claimDue counted included —
            // rather than being spent on a call that was never placed.
            Campaign campaign = task.campaignId() != null
                    ? campaignUseCase.findCampaign(task.companyId(), task.campaignId()) : null;
            if (campaign != null && (campaign.status() != CampaignStatus.ACTIVE
                    || !dialerService.isWithinDialWindow(campaign))) {
                log.info("Target {} ({}) not dialled: campaign {} is {}{}", task.targetId(),
                        PhoneMasker.mask(task.phone()), campaign.id(), campaign.status(),
                        campaign.status() == CampaignStatus.ACTIVE ? " but outside its dial window" : "");
                state.release(task.companyId(), task.campaignId());
                callBilling.releaseReservation(task.companyId(), task.targetId());
                campaignTargetUseCase.releaseClaim(task.targetId(), Instant.now());
                return;
            }

            // Ahead of everything else, because it decides the script the facts are read
            // against and the voice the call is warmed up for.
            AiAgent agent = aiAgentUseCase.requirePublishedAgent(task.companyId(), task.aiAgentId());
            Scenario scenario = aiAgentUseCase.resolveScenario(task.companyId(), agent);
            CrmClientSnapshot crm = crmUseCase.fetchClient(task.companyId(), task.clientId());
            ClientMemory memory = clientMemoryUseCase.findByCompanyIdAndPhone(task.companyId(), task.phone());
            CallContext context = CallContextFactory.mergeCrmFacts(
                    CallContextFactory.readContextData(task.contextData(), null, scenario.definition().factSchema()), crm)
                    .withMemory(memory);
            // Last and freshest. Deliberately here rather than once the call is up: the
            // number has not been dialled yet, so a slow endpoint costs this call's place in
            // the queue and not a silence the person who answered has to sit through.
            if (scenario.definition().factWebhook() != null) {
                context = CallContextFactory.overlayWebhookFacts(context,
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
            String firstMessage = CallContextFactory.readFirstMessageOverride(task.contextData());
            if (firstMessage != null) {
                agent = agent.withFirstMessage(firstMessage);
            }

            OutboundCall outboundCall = new OutboundCall(
                    task.campaignId(), task.targetId(), task.clientId(), task.phone(),
                    language, voice, context, agent, task.companyId(), task.sipTrunkId(),
                    task.variantId(), task.promptOverride(), task.pipelineModeOverride());

            // Originate with atomic registry pre-arming to eliminate StasisStart race condition
            originateReached = true;
            String channelId = ariService.originate(task.phone(), task.companyId(), outboundCall);

            audit.record(task.companyId(), "CALL_ORIGINATE", "call", channelId,
                    "target " + task.targetId() + " -> " + task.phone() + " (" + language + ")");
            log.info("Originated target {} -> channel {} (lang={}, trunkId={})",
                    task.targetId(), channelId, language, task.sipTrunkId());
        } catch (Exception e) {
            log.warn("Originate failed for target {} ({}): {}", task.targetId(), PhoneMasker.mask(task.phone()),
                    e.getMessage());
            state.release(task.companyId(), task.campaignId());
            // The hold is given back either way: a call that never reached Asterisk has no
            // finalizer to settle it, and one whose originate threw leaves no attempt row
            // to charge against.
            callBilling.releaseReservation(task.companyId(), task.targetId());
            if (!originateReached) {
                callAttemptUseCase.recordUnplacedAttempt(task.companyId(), task.targetId(), task.phone(),
                        task.language(), Disposition.FAILED, "call preparation failed: " + e.getMessage());
            }
            campaignTargetUseCase.applyOutcome(task.companyId(), task.targetId(), Disposition.FAILED);
        }
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
