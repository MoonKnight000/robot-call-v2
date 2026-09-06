package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallAttemptRepository;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository.CampaignTargetJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.repository.InboundRouteJpaRepository;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.Optional;

@Component
public class CallAttemptRepositoryAdapter implements CallAttemptRepository {

    private final CallAttemptJpaRepository jpa;
    private final CampaignTargetJpaRepository targets;
    private final CompanyJpaRepository companies;
    private final InboundRouteJpaRepository inboundRoutes;
    private final UserJpaRepository users;
    private final StoredFileJpaRepository storedFiles;

    public CallAttemptRepositoryAdapter(CallAttemptJpaRepository jpa,
                                       CampaignTargetJpaRepository targets,
                                       CompanyJpaRepository companies,
                                       InboundRouteJpaRepository inboundRoutes,
                                       UserJpaRepository users,
                                       StoredFileJpaRepository storedFiles) {
        this.jpa = jpa;
        this.targets = targets;
        this.companies = companies;
        this.inboundRoutes = inboundRoutes;
        this.users = users;
        this.storedFiles = storedFiles;
    }

    @Override
    public Long findCompanyIdById(long id) {
        return jpa.findCompanyIdById(id);
    }

    @Override
    public Long findTargetIdById(long id) {
        return jpa.findTargetIdById(id);
    }

    @Override
    public String findPhoneById(long id) {
        return jpa.findPhoneById(id);
    }

    @Override
    public Optional<Long> findTargetIdByPhone(String phone) {
        return targets.findFirstByPhoneOrderById(phone).map(CampaignTargetEntity::getId);
    }

    @Override
    public long startAttempt(long targetId, String channelId, String phone, String language,
                             Long inboundRouteId, long companyId) {
        Instant now = Instant.now();
        CallAttemptEntity entity = new CallAttemptEntity();
        entity.setTarget(targets.getReferenceById(targetId));
        entity.setAsteriskChannel(channelId);
        entity.setPhone(phone);
        entity.setLanguage(language);
        entity.setStartedAt(now);
        // answered_at stays null until someone picks up — the row now exists from the
        // moment the call is dialled, so it is what tells an answered call from a
        // rejected one.
        entity.setCreatedAt(now);
        entity.setCompany(companies.getReferenceById(companyId));
        if (inboundRouteId != null) {
            entity.setInboundRoute(inboundRoutes.getReferenceById(inboundRouteId));
        }
        return jpa.save(entity).getId();
    }

    /**
     * A call that never reached the trunk still leaves a row: the target's attempt
     * counter was already spent when the dialer claimed it, so without this the attempt
     * is invisible in the call list and only a log line says why it never happened.
     * Closed on insert — there is no channel that could close it later.
     */
    @Override
    public long recordUnplacedAttempt(long companyId, long targetId, String phone, String language,
                                      Disposition disposition, String reason) {
        Instant now = Instant.now();
        CallAttemptEntity entity = new CallAttemptEntity();
        entity.setTarget(targets.getReferenceById(targetId));
        entity.setPhone(phone);
        entity.setLanguage(language);
        entity.setStartedAt(now);
        entity.setEndedAt(now);
        entity.setDurationSec(0);
        entity.setDisposition(disposition);
        entity.setErrorMessage(reason);
        entity.setCreatedAt(now);
        entity.setCompany(companies.getReferenceById(companyId));
        return jpa.save(entity).getId();
    }

    @Override
    public Optional<Long> findIdByChannel(String channelId) {
        return jpa.findIdsByChannel(channelId, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Override
    public void markAnswered(long callId, Instant answeredAt) {
        jpa.markAnswered(callId, answeredAt);
    }

    @Override
    public void finishUnanswered(String channelId, Instant endedAt, Disposition disposition) {
        jpa.finishUnanswered(channelId, endedAt, disposition);
    }

    @Override
    public void recordError(long callId, String message) {
        jpa.recordError(callId, message);
    }

    @Override
    public void updateLanguage(long callId, String language) {
        jpa.updateLanguage(callId, language);
    }

    @Override
    public void recordHangupCause(String channelId, String cause) {
        jpa.recordHangupCause(channelId, cause);
    }

    @Override
    public void assignOperator(long callId, long userId) {
        jpa.assignOperator(callId, userId);
    }

    @Override
    public void finishAttempt(long callId, Instant endedAt, int durationSec, Disposition disposition, Long recordingFileId) {
        jpa.finishAttempt(callId, endedAt, durationSec, disposition, recordingFileId);
    }
}
