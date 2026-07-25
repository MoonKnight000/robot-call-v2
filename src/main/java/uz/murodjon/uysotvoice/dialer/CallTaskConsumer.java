package uz.murodjon.uysotvoice.dialer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.ari.AriService;
import uz.murodjon.uysotvoice.agent.dialog.CallContext;
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

    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            DialerState state, CampaignService campaignService) {
        this.ariService = ariService;
        this.registry = registry;
        this.state = state;
        this.campaignService = campaignService;
    }

    @RabbitListener(queues = RabbitConfig.CALL_TASK_QUEUE)
    public void onTask(CallTask task) {
        try {
            String channelId = ariService.originate(task.phone());
            CallContext context = CallContextMapper.fromJson(task.contextData(), null);
            registry.register(channelId, new OutboundCall(
                    task.campaignId(), task.targetId(), task.clientId(), task.language(), context));
            log.info("Originated target {} -> channel {}", task.targetId(), channelId);
        } catch (Exception e) {
            log.warn("Originate failed for target {} ({}): {}", task.targetId(), task.phone(), e.getMessage());
            state.release();
            campaignService.applyOutcome(task.targetId(), Disposition.FAILED);
        }
    }
}
