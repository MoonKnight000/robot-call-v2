package uz.murodjon.robotcallv2.contact.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactCrmPushUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;
import uz.murodjon.robotcallv2.contact.domain.service.CrmPushRetryPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContactCrmPushService implements ContactCrmPushUseCase {

    private static final int MAX_ERROR_LENGTH = 500;

    private final ContactCrmLinkRepository contactCrmLinkRepository;
    private final ContactRepository contactRepository;

    public ContactCrmPushService(ContactCrmLinkRepository contactCrmLinkRepository, ContactRepository contactRepository) {
        this.contactCrmLinkRepository = contactCrmLinkRepository;
        this.contactRepository = contactRepository;
    }

    @Override
    public List<PendingCrmPush> findDueCrmPushes(Instant now, int limit) {
        List<PendingCrmPush> due = new ArrayList<>();
        for (PendingCrmPush push : contactCrmLinkRepository.findDuePushes(now, limit)) {
            Contact contact = contactRepository.find(push.companyId(), push.contactId());
            if (contact != null) {
                due.add(new PendingCrmPush(push.companyId(), push.contactId(), push.provider(), push.externalId(),
                        push.requestedAt(), push.attempts(), contact.customFields()));
            }
        }
        return due;
    }

    @Override
    public void finishCrmPush(PendingCrmPush push, String error) {
        Instant now = Instant.now();
        if (error == null) {
            contactCrmLinkRepository.savePushSuccess(push, now);
            return;
        }
        int attempts = push.attempts() + 1;
        Instant next = CrmPushRetryPolicy.isExhausted(attempts) ? null : CrmPushRetryPolicy.findNextAttemptAt(now, attempts);
        String trimmed = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        contactCrmLinkRepository.savePushFailure(push, attempts, next, trimmed);
    }
}
