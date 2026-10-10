package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
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
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.output.PhoneNumberScrubberPort;
import uz.murodjon.robotcallv2.dialer.domain.entity.ScrubResult;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
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

    /** How long a full instance waits before handing a task back to the queue. */
    private static final long REQUEUE_PAUSE_MS = 1000;

    private final AriService ariService;
    private final OutboundCallRegistry registry;
    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final AuditUseCase audit;
    private final DialerState dialerState;
    private final DoNotCallUseCase doNotCallUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final OutboundCallPreparer outboundCallPreparer;
    private final CallBillingUseCase callBilling;
    /** Whose dial window the task is re-checked against at the last moment. */
    private final DialerService dialerService;
    private final CompanyUseCase companyUseCase;
    /** This instance's media ports — what a call answered here will need one of. */
    private final RtpPortAllocator portAllocator;
    private final PhoneNumberScrubberPort phoneNumberScrubber;

    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                            AuditUseCase audit, DialerState dialerState,
                            DoNotCallUseCase doNotCallUseCase, CallAttemptUseCase callAttemptUseCase,
                            OutboundCallPreparer outboundCallPreparer, CallBillingUseCase callBilling,
                            DialerService dialerService, CompanyUseCase companyUseCase,
                            RtpPortAllocator portAllocator) {
        this(ariService, registry, campaignUseCase, campaignTargetUseCase, audit, dialerState,
                doNotCallUseCase, callAttemptUseCase, outboundCallPreparer, callBilling, dialerService,
                companyUseCase, portAllocator, new uz.murodjon.robotcallv2.dialer.domain.service.PhoneNumberScrubber()::scrub);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CallTaskConsumer(AriService ariService, OutboundCallRegistry registry,
                            CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                            AuditUseCase audit, DialerState dialerState,
                            DoNotCallUseCase doNotCallUseCase, CallAttemptUseCase callAttemptUseCase,
                            OutboundCallPreparer outboundCallPreparer, CallBillingUseCase callBilling,
                            DialerService dialerService, CompanyUseCase companyUseCase,
                            RtpPortAllocator portAllocator,
                            PhoneNumberScrubberPort phoneNumberScrubber) {
        this.dialerService = dialerService;
        this.companyUseCase = companyUseCase;
        this.ariService = ariService;
        this.registry = registry;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.audit = audit;
        this.dialerState = dialerState;
        this.doNotCallUseCase = doNotCallUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.outboundCallPreparer = outboundCallPreparer;
        this.callBilling = callBilling;
        this.portAllocator = portAllocator;
        this.phoneNumberScrubber = phoneNumberScrubber;
    }

    @RabbitListener(queues = CallTask.QUEUE)
    public void onMessage(CallTask task) {
        if (!hasMediaHeadroom()) {
            // The dispatcher counts the cluster's ports, so the call fits somewhere — just
            // not here: answered on this instance it would be dropped for want of a port.
            // Back to the queue for another instance. The pause keeps this one from taking
            // it straight back, and stops it consuming until a call of its own has ended.
            log.info("No free RTP port for target {} on this instance — returning it to the queue",
                    task.targetId());
            try {
                Thread.sleep(REQUEUE_PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new ImmediateRequeueAmqpException("no free RTP port on this instance");
        }
        process(task, dialerState);
    }

    /** A free port for this call once the ones already ringing here have taken theirs. */
    private boolean hasMediaHeadroom() {
        return portAllocator.available() - registry.countUnanswered() > 0;
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

            // Guard: pre-call phone number scrubbing (format, unallocated prefix, reachability)
            ScrubResult scrubResult = phoneNumberScrubber.scrub(task.phone());
            if (!scrubResult.valid()) {
                log.info("Target {} ({}) scrubbed as invalid ({}) — skipping call origination",
                        task.targetId(), PhoneMasker.mask(task.phone()), scrubResult.reason());
                state.release(task.companyId(), task.campaignId());
                callBilling.releaseReservation(task.companyId(), task.targetId());
                callAttemptUseCase.recordUnplacedAttempt(task.companyId(), task.targetId(), task.phone(),
                        task.language(), Disposition.CARRIER_REJECTED, "scrubbed: " + scrubResult.reason());
                campaignTargetUseCase.applyOutcome(task.companyId(), task.targetId(), Disposition.CARRIER_REJECTED);
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

            if (campaign != null && !dialerService.isWithinDialWindowForRecipient(campaign, task.phone())) {
                log.info("Target {} ({}) not dialled: outside recipient local dial window / quiet hours",
                        task.targetId(), PhoneMasker.mask(task.phone()));
                state.release(task.companyId(), task.campaignId());
                callBilling.releaseReservation(task.companyId(), task.targetId());
                campaignTargetUseCase.releaseClaim(task.targetId(), Instant.now().plus(java.time.Duration.ofMinutes(30)));
                return;
            }

            OutboundCall outboundCall = outboundCallPreparer.prepare(task);

            // Originate with atomic registry pre-arming to eliminate StasisStart race condition
            originateReached = true;
            String channelId = ariService.originate(task.phone(), task.companyId(), outboundCall);

            audit.record(task.companyId(), "CALL_ORIGINATE", "call", channelId,
                    "target " + task.targetId() + " -> " + task.phone() + " (" + outboundCall.language() + ")");
            log.info("Originated target {} -> channel {} (lang={}, trunkId={})",
                    task.targetId(), channelId, outboundCall.language(), task.sipTrunkId());
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
}
