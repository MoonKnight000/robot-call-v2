package uz.murodjon.robotcallv2.callqueue.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callqueue.application.dto.SaveCallQueueRequest;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.output.CallQueueRepository;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueFilter;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;
import uz.murodjon.robotcallv2.callqueue.domain.service.CallQueueValidator;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.voicemail.application.port.input.VoicemailUseCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Operator queues (docs/INBOUND-PBX-PLAN.md §7.3). Everything a queue points at — its
 * members, hold music, overflow target, callback campaign, waiting agent — is checked to
 * be the company's own, because a live call follows those ids without asking again.
 */
@Service
public class CallQueueService implements CallQueueUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallQueueService.class);

    private final CallQueueRepository repository;
    private final UserUseCase userUseCase;
    private final VoicemailUseCase voicemailUseCase;
    private final FileStorageUseCase fileStorageUseCase;
    /** Lazy both: an agent names its transfer queue, and a campaign names its agent. */
    private final AiAgentUseCase aiAgentUseCase;
    private final CampaignUseCase campaignUseCase;
    private final AuditUseCase auditService;

    public CallQueueService(CallQueueRepository repository, UserUseCase userUseCase, VoicemailUseCase voicemailUseCase,
                            FileStorageUseCase fileStorageUseCase, @Lazy AiAgentUseCase aiAgentUseCase,
                            @Lazy CampaignUseCase campaignUseCase, AuditUseCase auditService) {
        this.repository = repository;
        this.userUseCase = userUseCase;
        this.voicemailUseCase = voicemailUseCase;
        this.fileStorageUseCase = fileStorageUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.campaignUseCase = campaignUseCase;
        this.auditService = auditService;
    }

    @Override
    public CallQueue create(long companyId, SaveCallQueueRequest request) {
        CallQueue queue = toQueue(companyId, request);
        CallQueueValidator.validate(queue);
        requireReferences(companyId, queue, null);
        long id = repository.create(companyId, queue);
        auditService.record(companyId, "CALL_QUEUE_CREATE", "call_queue", String.valueOf(id), queue.name());
        return requireQueue(companyId, id);
    }

    @Override
    public CallQueue update(long companyId, long id, SaveCallQueueRequest request) {
        requireQueue(companyId, id);
        CallQueue queue = toQueue(companyId, request);
        CallQueueValidator.validate(queue);
        requireReferences(companyId, queue, id);
        repository.update(companyId, id, queue);
        auditService.record(companyId, "CALL_QUEUE_UPDATE", "call_queue", String.valueOf(id), queue.name());
        return requireQueue(companyId, id);
    }

    @Override
    public void delete(long companyId, long id) {
        requireQueue(companyId, id);
        repository.delete(companyId, id);
        auditService.record(companyId, "CALL_QUEUE_DELETE", "call_queue", String.valueOf(id), null);
    }

    @Override
    public CallQueue requireQueue(long companyId, long id) {
        CallQueue queue = repository.find(companyId, id);
        if (queue == null) {
            throw new NotFoundException(ErrorCode.CALL_QUEUE_NOT_FOUND, id);
        }
        return queue;
    }

    @Override
    public CallQueue findQueue(long companyId, long id) {
        return repository.find(companyId, id);
    }

    @Override
    public PageableData<CallQueue> list(long companyId, CallQueueFilter filter) {
        return PageableData.of(repository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                repository.count(companyId));
    }

    @Override
    public List<CallQueue> findEnabledByCompanyId(long companyId) {
        return repository.findEnabledByCompanyId(companyId);
    }

    private void requireReferences(long companyId, CallQueue queue, Long selfId) {
        for (CallQueueMember member : queue.members()) {
            userUseCase.requireUser(companyId, member.userId());
        }
        if (queue.waitingAgentId() != null) {
            aiAgentUseCase.requireAgent(companyId, queue.waitingAgentId());
        }
        if (queue.callbackCampaignId() != null) {
            campaignUseCase.requireCampaign(companyId, queue.callbackCampaignId());
        }
        if (queue.holdMusicFileId() != null) {
            requireOwnFile(companyId, queue.holdMusicFileId());
        }
        Long target = queue.overflowTargetId();
        switch (queue.overflowAction()) {
            case QUEUE -> {
                if (target.equals(selfId)) {
                    throw new ValidationException(ErrorCode.CALL_QUEUE_OVERFLOW_TO_SELF);
                }
                requireQueue(companyId, target);
            }
            case AI_AGENT -> aiAgentUseCase.requireAgent(companyId, target);
            case VOICEMAIL -> voicemailUseCase.requireBox(companyId, target);
            default -> {
                // nothing to look up
            }
        }
    }

    private void requireOwnFile(long companyId, long fileId) {
        DownloadableFile file = fileStorageUseCase.download(companyId, fileId);
        try (InputStream ignored = file.content()) {
            // only the access check mattered
        } catch (IOException e) {
            log.debug("Closing hold music file {} failed: {}", fileId, e.getMessage());
        }
    }

    private static CallQueue toQueue(long companyId, SaveCallQueueRequest request) {
        QueueOverflowAction overflow = request.overflowAction() != null ? request.overflowAction() : QueueOverflowAction.CALLBACK;
        boolean needsTarget = overflow != QueueOverflowAction.CALLBACK && overflow != QueueOverflowAction.HANGUP;
        return new CallQueue(0, companyId, request.name().trim(),
                request.strategy() != null ? request.strategy() : CallQueueStrategy.LEAST_RECENT,
                valueOr(request.ringTimeoutSec(), 15),
                valueOr(request.wrapUpSec(), 10),
                valueOr(request.maxWaitSec(), 300),
                valueOr(request.maxLength(), 0),
                valueOr(request.announcePositionEverySec(), 60),
                request.announceWaitTime() == null || request.announceWaitTime(),
                blankToNull(request.periodicAnnounceText()),
                valueOr(request.periodicAnnounceEverySec(), 0),
                request.holdMusicFileId(),
                valueOr(request.callbackOfferAfterSec(), 0),
                request.callbackCampaignId(),
                overflow,
                needsTarget ? blankToNull(request.overflowTarget()) : null,
                valueOr(request.serviceLevelSec(), 20),
                valueOr(request.stickySeconds(), 0),
                request.postCallSurvey() != null && request.postCallSurvey(),
                request.waitingAgentId(),
                request.autoCallback() != null && request.autoCallback(),
                request.enabled() == null || request.enabled(),
                request.members(),
                null);
    }

    private static int valueOr(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
