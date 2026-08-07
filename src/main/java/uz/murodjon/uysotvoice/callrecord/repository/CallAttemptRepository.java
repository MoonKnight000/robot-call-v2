package uz.murodjon.uysotvoice.callrecord.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.callrecord.entity.CallAttemptEntity;
import uz.murodjon.uysotvoice.campaign.entity.CampaignTargetEntity;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetJpaRepository;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Optional;

/** JPA-backed DAO for {@code call_attempt} (PROJECT.md §6, Stage 9). */
@Repository
public class CallAttemptRepository {

    private final CallAttemptJpaRepository jpa;
    private final CampaignTargetJpaRepository targets;

    public CallAttemptRepository(CallAttemptJpaRepository jpa, CampaignTargetJpaRepository targets) {
        this.jpa = jpa;
        this.targets = targets;
    }

    /** Which company an in-flight call belongs to (§11 ai-model settings) — {@code DialogEngine} resolves this once per call. */
    public Long findCompanyIdById(long id) {
        return jpa.findCompanyIdById(id);
    }

    /** The placeholder target seeded for manual/inbound test calls (phone = {@code "MANUAL"}/{@code "INBOUND"}). */
    public Optional<Long> findTargetIdByPhone(String phone) {
        return targets.findFirstByPhoneOrderById(phone).map(CampaignTargetEntity::getId);
    }

    /**
     * Insert a call_attempt (answered now); returns its id.
     *
     * @param inboundRouteId the route this call matched (ROADMAP C.1, §10.9 stats
     *                       drawer), or null for an outbound/manual call
     */
    public long startAttempt(long targetId, String channelId, String language, Long inboundRouteId, long companyId) {
        Instant now = Instant.now();
        CallAttemptEntity entity = new CallAttemptEntity();
        entity.setTarget(targets.getReferenceById(targetId));
        entity.setAsteriskChannel(channelId);
        entity.setLanguage(language);
        entity.setStartedAt(now);
        entity.setAnsweredAt(now);
        entity.setCreatedAt(now);
        entity.setCompanyId(companyId);
        entity.setInboundRouteId(inboundRouteId);
        return jpa.save(entity).getId();
    }

    /**
     * Record why a call failed technically, on the attempt row. Without this the
     * {@code error_message} column stays empty and a call that died during media
     * setup is indistinguishable from one the client simply did not answer.
     */
    public void recordError(long callId, String message) {
        jpa.recordError(callId, message);
    }

    /**
     * Store the Asterisk hangup cause for the attempt on {@code channelId} (§8.6).
     *
     * <p>Keyed by channel rather than by attempt id because the cause arrives on
     * {@code ChannelDestroyed}, which fires after the attempt has already been closed out
     * — and for a call that was never answered there is no attempt row in memory at all.
     */
    public void recordHangupCause(String channelId, String cause) {
        jpa.recordHangupCause(channelId, cause);
    }

    /** §15 "Bugun" mini-statistika — which panel user answered a transferred call. */
    public void assignOperator(long callId, long userId) {
        jpa.assignOperator(callId, userId);
    }

    /** Close out a call_attempt with its outcome. */
    public void finishAttempt(long callId, Instant endedAt, int durationSec, Disposition disposition,
                              Long recordingFileId) {
        jpa.finishAttempt(callId, endedAt, durationSec, disposition, recordingFileId);
    }
}
