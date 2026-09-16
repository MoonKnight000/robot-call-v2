package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallAttemptRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignVariantJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundRouteJpaRepository;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class CallAttemptRepositoryAdapter implements CallAttemptRepository {

    private final CallAttemptJpaRepository callAttemptJpaRepository;
    private final CampaignTargetJpaRepository campaignTargetJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final InboundRouteJpaRepository inboundRouteJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final StoredFileJpaRepository storedFileJpaRepository;
    private final CampaignVariantJpaRepository campaignVariantJpaRepository;

    public CallAttemptRepositoryAdapter(CallAttemptJpaRepository callAttemptJpaRepository,
                                       CampaignTargetJpaRepository campaignTargetJpaRepository,
                                       CompanyJpaRepository companyJpaRepository,
                                       InboundRouteJpaRepository inboundRouteJpaRepository,
                                       UserJpaRepository userJpaRepository,
                                       StoredFileJpaRepository storedFileJpaRepository,
                                        CampaignVariantJpaRepository campaignVariantJpaRepository) {
        this.callAttemptJpaRepository = callAttemptJpaRepository;
        this.campaignTargetJpaRepository = campaignTargetJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.inboundRouteJpaRepository = inboundRouteJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.storedFileJpaRepository = storedFileJpaRepository;
        this.campaignVariantJpaRepository = campaignVariantJpaRepository;
    }

    @Override
    public Long findCompanyIdById(long id) {
        return callAttemptJpaRepository.findCompanyIdById(id);
    }

    @Override
    public Long findTargetIdById(long id) {
        return callAttemptJpaRepository.findTargetIdById(id);
    }

    @Override
    public String findPhoneById(long id) {
        return callAttemptJpaRepository.findPhoneById(id);
    }

    @Override
    public Boolean findInboundById(long id) {
        return callAttemptJpaRepository.findInboundById(id);
    }

    @Override
    public Long findInboundRouteIdById(long id) {
        return callAttemptJpaRepository.findInboundRouteIdById(id);
    }

    /**
     * The row exists from the moment the call is dialled: {@code answered_at} staying null
     * is what tells an answered call from a rejected one. An attempt that never reached
     * the trunk arrives here already closed ({@code endedAt} set) — the target's attempt
     * counter was spent when the dialer claimed it, and there is no channel that could
     * close the row later.
     */
    @Override
    public long create(CallAttempt attempt) {
        Instant now = Instant.now();
        CallAttemptEntity entity = new CallAttemptEntity();
        entity.setTarget(attempt.targetId() > 0 ? campaignTargetJpaRepository.getReferenceById(attempt.targetId()) : null);
        entity.setAsteriskChannel(attempt.asteriskChannel());
        entity.setPhone(attempt.phone());
        entity.setLanguage(attempt.language());
        entity.setStartedAt(now);
        entity.setCreatedAt(now);
        entity.setCompany(companyJpaRepository.getReferenceById(attempt.companyId()));
        entity.setVariant(variantReference(attempt.variantId()));
        if (attempt.inboundRouteId() != null) {
            entity.setInboundRoute(inboundRouteJpaRepository.getReferenceById(attempt.inboundRouteId()));
        }
        if (attempt.disposition() != null) {
            entity.setEndedAt(now);
            // Null unless the caller measured one: reports read a null duration as "never
            // connected", and the zero this used to write counted never-dialled attempts
            // as answered calls.
            entity.setDurationSec(attempt.durationSec());
            entity.setDisposition(attempt.disposition());
            entity.setErrorMessage(attempt.errorMessage());
        }
        return callAttemptJpaRepository.save(entity).getId();
    }

    @Override
    public List<AnsweredCall> findAnsweredByPhone(long companyId, String phone, Instant from, Instant to) {
        return callAttemptJpaRepository.findAnsweredByPhone(companyId, phone, from, to).stream()
                .map(row -> new AnsweredCall(
                        ((Number) row[0]).longValue(),
                        row[1] != null ? ((Number) row[1]).longValue() : null,
                        row[2] != null ? ((Number) row[2]).longValue() : null,
                        (Instant) row[3]))
                .toList();
    }

    @Override
    public ContactHistory findContactHistory(long companyId, String phone, Instant since, int attemptLimit) {
        List<Instant> attempts = callAttemptJpaRepository
                .findRecentPlacedStartTimes(companyId, phone, since, attemptLimit).stream()
                .map(CallAttemptRepositoryAdapter::toInstant)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ContactHistory(attempts,
                toInstant(callAttemptJpaRepository.findLastConversationAt(companyId, phone)),
                toLocalDate(callAttemptJpaRepository.findLatestPromisedDate(companyId, phone)));
    }

    /** A native timestamp column comes back as whichever type the driver prefers. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }

    private static java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.time.LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return null;
    }

    @Override
    public Optional<Long> findIdByChannel(String channelId) {
        return callAttemptJpaRepository.findIdsByChannel(channelId, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Override
    public long countEndedSince(Instant since) {
        return callAttemptJpaRepository.countByEndedAtGreaterThanEqual(since);
    }

    @Override
    public long countEndedSinceWithDisposition(Instant since, Disposition disposition) {
        return callAttemptJpaRepository.countByEndedAtGreaterThanEqualAndDisposition(since, disposition);
    }

    @Override
    public void markAnswered(long callId, Instant answeredAt) {
        callAttemptJpaRepository.markAnswered(callId, answeredAt);
    }

    @Override
    public void finishUnanswered(String channelId, Instant endedAt, Disposition disposition) {
        callAttemptJpaRepository.finishUnanswered(channelId, endedAt, disposition);
    }

    @Override
    public void recordError(long callId, String message) {
        callAttemptJpaRepository.recordError(callId, message);
    }

    @Override
    public void updateLanguage(long callId, String language) {
        callAttemptJpaRepository.updateLanguage(callId, language);
    }

    @Override
    public void recordHangupCause(String channelId, String cause) {
        callAttemptJpaRepository.recordHangupCause(channelId, cause);
    }

    @Override
    public void assignOperator(long callId, long userId) {
        callAttemptJpaRepository.assignOperator(callId, userId);
    }

    @Override
    public void markEnqueued(long callId, long queueId, Instant at) {
        callAttemptJpaRepository.markEnqueued(callId, queueId, at);
    }

    @Override
    public void markOperatorAnswered(long callId, long userId, Instant at, int waitSeconds) {
        callAttemptJpaRepository.markOperatorAnswered(callId, userId, at, waitSeconds);
    }

    @Override
    public void markAbandoned(long callId, int waitSeconds) {
        callAttemptJpaRepository.markAbandoned(callId, waitSeconds);
    }

    @Override
    public void recordSurveyScore(long callId, int score) {
        callAttemptJpaRepository.recordSurveyScore(callId, score);
    }

    @Override
    public Long findLastOperatorUserId(long companyId, String phone, Instant since) {
        return callAttemptJpaRepository.findLastOperatorUserId(companyId, phone, since).stream().findFirst().orElse(null);
    }

    @Override
    public void finishAttempt(long callId, Instant endedAt, int durationSec, Disposition disposition, Long recordingFileId) {
        callAttemptJpaRepository.finishAttempt(callId, endedAt, durationSec, disposition, recordingFileId);
    }

    @Override
    public void updateDisposition(long callId, Disposition disposition) {
        callAttemptJpaRepository.updateDisposition(callId, disposition);
    }

    /** Null for a call the campaign dialled without an A/B variant. */
    private CampaignVariantEntity variantReference(Long id) {
        return id != null ? campaignVariantJpaRepository.getReferenceById(id) : null;
    }

    @Override
    public void recordAgent(long callId, Long agentId, Integer agentVersion) {
        callAttemptJpaRepository.recordAgent(callId, agentId, agentVersion);
    }

    @Override
    public List<Long> findExpiredRecordingFileIds(ConversationRetention retention, int batchSize) {
        return callAttemptJpaRepository.findExpiredRecordingFileIds(retention.now(), retention.defaultCutoff(),
                retention.zeroPiiCutoff(), batchSize);
    }
}
