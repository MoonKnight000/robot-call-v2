package uz.murodjon.robotcallv2.voicemail.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.operator.application.port.input.ContactCenterEventUseCase;
import uz.murodjon.robotcallv2.operator.domain.enums.ContactCenterEventType;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.voicemail.application.dto.SaveVoicemailBoxRequest;
import uz.murodjon.robotcallv2.voicemail.application.dto.UpdateVoicemailStatusRequest;
import uz.murodjon.robotcallv2.voicemail.application.port.input.VoicemailUseCase;
import uz.murodjon.robotcallv2.voicemail.application.port.output.VoicemailBoxRepository;
import uz.murodjon.robotcallv2.voicemail.application.port.output.VoicemailMessageRepository;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBoxFilter;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessageFilter;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;
import uz.murodjon.robotcallv2.voicemail.domain.service.VoicemailBoxValidator;

import java.io.IOException;
import java.io.InputStream;

/**
 * Voicemail boxes and the messages callers leave in them (docs/INBOUND-PBX-PLAN.md §8.1).
 * Recording happens on the call path; this stores the result, and tells the company.
 */
@Service
public class VoicemailService implements VoicemailUseCase {

    private static final Logger log = LoggerFactory.getLogger(VoicemailService.class);
    private static final int DEFAULT_MAX_SECONDS = 120;
    /** How much of the transcript the notification carries — the rest is one click away. */
    private static final int NOTIFICATION_TRANSCRIPT_CHARS = 200;

    private final VoicemailBoxRepository boxRepository;
    private final VoicemailMessageRepository messageRepository;
    private final FileStorageUseCase fileStorageService;
    private final NotificationUseCase notificationService;
    private final ContactCenterEventUseCase contactCenterEventUseCase;
    private final AuditUseCase auditService;

    public VoicemailService(VoicemailBoxRepository boxRepository, VoicemailMessageRepository messageRepository,
                            FileStorageUseCase fileStorageService, NotificationUseCase notificationService,
                            ContactCenterEventUseCase contactCenterEventUseCase, AuditUseCase auditService) {
        this.boxRepository = boxRepository;
        this.messageRepository = messageRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.contactCenterEventUseCase = contactCenterEventUseCase;
        this.auditService = auditService;
    }

    @Override
    public VoicemailBox createBox(long companyId, SaveVoicemailBoxRequest request) {
        VoicemailBox box = toBox(companyId, request);
        long id = boxRepository.create(companyId, box);
        auditService.record(companyId, "VOICEMAIL_BOX_CREATE", "voicemail_box", String.valueOf(id), box.name());
        return requireBox(companyId, id);
    }

    @Override
    public VoicemailBox updateBox(long companyId, long id, SaveVoicemailBoxRequest request) {
        requireBox(companyId, id);
        VoicemailBox box = toBox(companyId, request);
        boxRepository.update(companyId, id, box);
        auditService.record(companyId, "VOICEMAIL_BOX_UPDATE", "voicemail_box", String.valueOf(id), box.name());
        return requireBox(companyId, id);
    }

    @Override
    public void deleteBox(long companyId, long id) {
        requireBox(companyId, id);
        boxRepository.delete(companyId, id);
        auditService.record(companyId, "VOICEMAIL_BOX_DELETE", "voicemail_box", String.valueOf(id), null);
    }

    @Override
    public VoicemailBox requireBox(long companyId, long id) {
        VoicemailBox box = boxRepository.find(companyId, id);
        if (box == null) {
            throw new NotFoundException(ErrorCode.VOICEMAIL_BOX_NOT_FOUND, id);
        }
        return box;
    }

    @Override
    public VoicemailBox findBox(long companyId, long id) {
        return boxRepository.find(companyId, id);
    }

    @Override
    public PageableData<VoicemailBox> listBoxes(long companyId, VoicemailBoxFilter filter) {
        return PageableData.of(boxRepository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                boxRepository.count(companyId));
    }

    @Override
    public PageableData<VoicemailMessage> listMessages(long companyId, VoicemailMessageFilter filter) {
        return PageableData.of(messageRepository.findAll(companyId, filter), filter.pageOrDefault(),
                filter.sizeOrDefault(), messageRepository.count(companyId, filter));
    }

    @Override
    public VoicemailMessage updateMessageStatus(long companyId, long id, UpdateVoicemailStatusRequest request) {
        requireMessage(companyId, id);
        messageRepository.updateStatus(companyId, id, request.status());
        return requireMessage(companyId, id);
    }

    @Override
    public DownloadableFile downloadAudio(long companyId, long id) {
        VoicemailMessage message = requireMessage(companyId, id);
        if (message.storedFileId() == null) {
            throw new NotFoundException(ErrorCode.VOICEMAIL_AUDIO_NOT_FOUND, id);
        }
        return fileStorageService.download(companyId, message.storedFileId());
    }

    @Override
    public VoicemailMessage recordMessage(long companyId, long boxId, Long callAttemptId, String callerPhone,
                                          byte[] wav, int durationSec, String transcript) {
        Long storedFileId = null;
        try {
            String name = "voicemail-" + (callAttemptId != null ? callAttemptId : System.currentTimeMillis()) + ".wav";
            StoredFile stored = fileStorageService.store(wav, companyId, FileCategory.AUDIO, name, "audio/wav");
            storedFileId = stored != null ? stored.id() : null;
        } catch (Exception e) {
            // The transcript and the caller's number still reach the operators — a message
            // without its audio is worth more than no message.
            log.warn("Voicemail audio for call {} could not be stored: {}", callAttemptId, e.getMessage());
        }
        long id = messageRepository.create(companyId, new VoicemailMessage(0, companyId, boxId, callAttemptId,
                callerPhone, storedFileId, durationSec, blankToNull(transcript), VoicemailStatus.NEW, null));
        VoicemailMessage message = messageRepository.find(companyId, id);
        notificationService.notify(companyId, NotificationType.VOICEMAIL_RECEIVED, "Yangi ovozli xabar",
                describe(message), null);
        contactCenterEventUseCase.publishToCompany(companyId, ContactCenterEventType.VOICEMAIL, message);
        return message;
    }

    private VoicemailMessage requireMessage(long companyId, long id) {
        VoicemailMessage message = messageRepository.find(companyId, id);
        if (message == null) {
            throw new NotFoundException(ErrorCode.VOICEMAIL_MESSAGE_NOT_FOUND, id);
        }
        return message;
    }

    private VoicemailBox toBox(long companyId, SaveVoicemailBoxRequest request) {
        int maxSeconds = request.maxSeconds() != null ? request.maxSeconds() : DEFAULT_MAX_SECONDS;
        VoicemailBoxValidator.validate(request.name(), request.greetingText(), maxSeconds);
        if (request.greetingFileId() != null) {
            requireOwnFile(companyId, request.greetingFileId());
        }
        return new VoicemailBox(0, companyId, request.name().trim(), blankToNull(request.greetingText()),
                request.greetingFileId(), maxSeconds, request.transcribe() == null || request.transcribe(),
                request.enabled() == null || request.enabled(), null);
    }

    /** Opening the file is the company check storage already has; the stream itself is not needed. */
    private void requireOwnFile(long companyId, long fileId) {
        DownloadableFile file = fileStorageService.download(companyId, fileId);
        try (InputStream ignored = file.content()) {
            // only the access check mattered
        } catch (IOException e) {
            log.debug("Closing greeting file {} failed: {}", fileId, e.getMessage());
        }
    }

    private static String describe(VoicemailMessage message) {
        String phone = message.callerPhone() != null ? message.callerPhone() : "noma'lum raqam";
        String transcript = message.transcript();
        if (transcript == null) {
            return phone + " — " + message.durationSec() + " soniyalik xabar";
        }
        String head = transcript.length() > NOTIFICATION_TRANSCRIPT_CHARS
                ? transcript.substring(0, NOTIFICATION_TRANSCRIPT_CHARS) + "…" : transcript;
        return phone + ": " + head;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
