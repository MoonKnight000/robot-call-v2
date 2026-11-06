package uz.murodjon.robotcallv2.crm.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactCrmPushUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.PendingCrmPush;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSyncRepository;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactWriterPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncSettings;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.shared.exception.AppException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes changed contact values back to the CRM record each contact was imported from, by the
 * company's push mappings (Retell's "Sync to CRM"). A write that fails is tried again later
 * with a growing pause; a contact with nothing to write, or whose CRM is no longer the
 * connected one, is let go.
 */
@Service
public class CrmContactPushService {

    private static final Logger log = LoggerFactory.getLogger(CrmContactPushService.class);

    private final ContactCrmPushUseCase contactCrmPushUseCase;
    private final CrmContactSyncRepository crmContactSyncRepository;
    private final CrmConnectionResolver crmConnectionResolver;
    private final List<CrmContactWriterPort> contactWriters;

    public CrmContactPushService(ContactCrmPushUseCase contactCrmPushUseCase,
                                 CrmContactSyncRepository crmContactSyncRepository,
                                 CrmConnectionResolver crmConnectionResolver,
                                 List<CrmContactWriterPort> contactWriters) {
        this.contactCrmPushUseCase = contactCrmPushUseCase;
        this.crmContactSyncRepository = crmContactSyncRepository;
        this.crmConnectionResolver = crmConnectionResolver;
        this.contactWriters = contactWriters;
    }

    /** Runs up to {@code limit} due writes, one after the other; called by the scheduler. */
    void runDuePushes(int limit) {
        for (PendingCrmPush push : contactCrmPushUseCase.findDueCrmPushes(Instant.now(), limit)) {
            try {
                contactCrmPushUseCase.finishCrmPush(push, push(push));
            } catch (Exception e) {
                log.error("CRM push of contact {} (company {}) crashed: {}", push.contactId(), push.companyId(),
                        e.getMessage(), e);
            }
        }
    }

    /** One write; {@code null} when done or when there is nothing to write, else why it failed. */
    String push(PendingCrmPush push) {
        CrmContactSyncSettings settings = crmContactSyncRepository.findSettings(push.companyId());
        if (settings == null || settings.pushMappings().isEmpty()) {
            return null;
        }
        CrmConnection connection = crmConnectionResolver.findConnection(push.companyId());
        if (connection == null || !connection.provider().name().equals(push.provider())) {
            // The record belongs to a CRM the company no longer uses: nowhere to write.
            return null;
        }
        CrmContactWriterPort writer = contactWriters.stream()
                .filter(candidate -> candidate.isSupported(connection.provider())).findFirst().orElse(null);
        if (writer == null) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (CrmContactFieldMapping mapping : settings.pushMappings()) {
            String value = push.customFields().get(mapping.contactField());
            // An empty contact field never clears the CRM's value.
            if (value != null && !value.isBlank()) {
                values.put(mapping.crmField(), value);
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        try {
            writer.updateContactFields(connection, push.externalId(), values);
            return null;
        } catch (Exception e) {
            // An AppException's message is ours to show; anything else is a bug, named by its type only.
            String error = e instanceof AppException ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("CRM push of contact {} (company {}) to {} failed: {}", push.contactId(), push.companyId(),
                    push.provider(), error);
            return error;
        }
    }
}
