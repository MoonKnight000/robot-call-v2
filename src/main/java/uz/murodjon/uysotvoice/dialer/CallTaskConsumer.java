package uz.murodjon.uysotvoice.dialer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.crm.CrmClient;
import uz.murodjon.uysotvoice.agent.crm.CrmClientSnapshot;
import uz.murodjon.uysotvoice.agent.dialog.CallContext;
import uz.murodjon.uysotvoice.audit.AuditService;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

/**
 * Consumes {@link CallTask} messages and originates the call (PROJECT.md §10). The
 * concurrency slot was reserved at dispatch; it is released later on hangup (by
 * {@code AriService.teardown}) or by the sweeper. On an origination failure the slot
 * is released here and the target retried.
 */
@Component
public class CallTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(CallTaskConsumer.class);

    private final AriService ariService;
    private final OutboundCallRegistry registry;
    private final DialerState state;
    private final CampaignService campaignService;
    private final CrmClient crmClient;
    private final AuditService audit;

    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            DialerState state, CampaignService campaignService,
                            CrmClient crmClient, AuditService audit) {
        this.ariService = ariService;
        this.registry = registry;
        this.state = state;
        this.campaignService = campaignService;
        this.crmClient = crmClient;
        this.audit = audit;
    }

    @RabbitListener(queues = RabbitConfig.CALL_TASK_QUEUE)
    public void onTask(CallTask task) {
        try {
            // Refresh the facts and the language BEFORE the phone rings: the imported
            // context_data is a snapshot from when the campaign was built, and the agent is
            // about to state its debt figure out loud (§9 step 4, §3.1).
            CrmClientSnapshot crm = crmClient.fetchClient(task.clientId());
            CallContext context = CallContextMapper.merge(
                    CallContextMapper.fromJson(task.contextData(), null), crm);
            String language = resolveLanguage(task, crm);

            String channelId = ariService.originate(task.phone());
            registry.register(channelId, new OutboundCall(
                    task.campaignId(), task.targetId(), task.clientId(), task.phone(),
                    language, task.ttsVoice(), context));
            audit.record("CALL_ORIGINATE", "call", channelId,
                    "target " + task.targetId() + " -> " + task.phone() + " (" + language + ")");
            log.info("Originated target {} -> channel {} (lang={})", task.targetId(), channelId, language);
        } catch (Exception e) {
            log.warn("Originate failed for target {} ({}): {}", task.targetId(), task.phone(), e.getMessage());
            state.release();
            campaignService.applyOutcome(task.targetId(), Disposition.FAILED);
        }
    }

    /**
     * The language this call speaks, in the §3.1 priority order: the client's own preference
     * from the CRM, then the target/campaign default the dispatcher chose.
     */
    private static String resolveLanguage(CallTask task, CrmClientSnapshot crm) {
        if (crm != null && crm.preferredLanguage() != null && !crm.preferredLanguage().isBlank()) {
            return crm.preferredLanguage();
        }
        return task.language();
    }
}
