package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelDestroyed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.DialerSlotUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.TrunkHealthUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

/**
 * {@code ChannelDestroyed}: the last word on every channel. Records its hangup cause, lets
 * the PBX side clean up after its legs, and settles a call that was never answered — the
 * attempt row, the dialer slot, the balance hold, the trunk's health and the campaign target.
 */
@Service
class UnansweredCallSettler {

    private static final Logger log = LoggerFactory.getLogger(UnansweredCallSettler.class);

    private final CallSessionRegistry callSessionRegistry;
    private final PbxLegService pbxLegService;
    private final CallAttemptUseCase callAttemptUseCase;
    private final PendingCallRegistry pendingCallRegistry;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final DialerSlotUseCase dialerSlotUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final TrunkHealthUseCase trunkHealthUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final VoiceMetrics metrics;

    UnansweredCallSettler(CallSessionRegistry callSessionRegistry,
                          PbxLegService pbxLegService,
                          CallAttemptUseCase callAttemptUseCase,
                          PendingCallRegistry pendingCallRegistry,
                          OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                          DialerSlotUseCase dialerSlotUseCase,
                          CallBillingUseCase callBillingUseCase,
                          TrunkHealthUseCase trunkHealthUseCase,
                          CampaignTargetUseCase campaignTargetUseCase,
                          VoiceMetrics metrics) {
        this.callSessionRegistry = callSessionRegistry;
        this.pbxLegService = pbxLegService;
        this.callAttemptUseCase = callAttemptUseCase;
        this.pendingCallRegistry = pendingCallRegistry;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.dialerSlotUseCase = dialerSlotUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.trunkHealthUseCase = trunkHealthUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.metrics = metrics;
    }

    /** Runs on the channel's event lane. */
    void onChannelDestroyed(ChannelDestroyed event) {
        Channel channel = event.getChannel();
        if (channel == null) {
            return;
        }
        String channelId = channel.getId();
        String name = channel.getName();
        if (ChannelNames.isExternalMedia(name)) {
            callSessionRegistry.removeHangupCause(channelId);
            return;
        }
        callSessionRegistry.rememberHangupCause(channel, event.getCause());
        Integer cause = callSessionRegistry.removeHangupCause(channelId);
        pbxLegService.notifyPbxLegGone(channelId, cause);

        callAttemptUseCase.recordHangupCause(channelId, HangupCause.label(cause));

        Disposition disposition = HangupCause.toDisposition(cause);
        if (disposition == null) {
            disposition = Disposition.NO_ANSWER;
        }
        callAttemptUseCase.finishUnansweredAttempt(channelId, disposition);
        pendingCallRegistry.forgetUnanswered(channelId);

        OutboundCall outbound = outboundCallTrackingUseCase.removeIfUnanswered(channelId);
        if (outbound == null) {
            return;
        }
        dialerSlotUseCase.release(outbound.companyId(), outbound.campaignId());
        // Only an answered call is settled (CallFinalizer); an unanswered one has to give
        // its hold back here, or every no-answer kept its reservation for good.
        callBillingUseCase.releaseReservation(outbound.companyId(), outbound.targetId());
        trunkHealthUseCase.recordOutcome(outbound.sipTrunkId(), disposition);
        campaignTargetUseCase.applyOutcome(outbound.companyId(), outbound.targetId(), disposition);
        metrics.disposition(disposition);
        log.info("Unanswered call {} to {} settled as {} (cause {})",
                channelId, PhoneMasker.mask(outbound.phone()), disposition, HangupCause.label(cause));
    }
}
