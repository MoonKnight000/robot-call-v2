package uz.murodjon.robotcallv2.contact.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelTokens;
import uz.murodjon.robotcallv2.contact.application.dto.CreateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.application.dto.UpdateContactFieldRequest;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactFieldUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldMergerPort;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.ExtractedCallValues;
import uz.murodjon.robotcallv2.contact.domain.entity.MergedFieldValue;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;
import uz.murodjon.robotcallv2.contact.domain.service.ContactFieldUpdatePolicy;
import uz.murodjon.robotcallv2.contact.domain.service.ContactFieldValidator;
import uz.murodjon.robotcallv2.contact.domain.service.ContactValidator;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContactFieldService implements ContactFieldUseCase {

    private static final Logger log = LoggerFactory.getLogger(ContactFieldService.class);

    private final ContactFieldRepository contactFieldRepository;
    private final ContactRepository contactRepository;
    private final AuditUseCase auditUseCase;
    private final ContactFieldMergerPort contactFieldMergerPort;
    private final CallBillingUseCase callBillingUseCase;
    private final ContactCrmLinkRepository contactCrmLinkRepository;

    public ContactFieldService(ContactFieldRepository contactFieldRepository, ContactRepository contactRepository,
                               AuditUseCase auditUseCase, ContactFieldMergerPort contactFieldMergerPort,
                               CallBillingUseCase callBillingUseCase, ContactCrmLinkRepository contactCrmLinkRepository) {
        this.contactFieldRepository = contactFieldRepository;
        this.contactRepository = contactRepository;
        this.auditUseCase = auditUseCase;
        this.contactFieldMergerPort = contactFieldMergerPort;
        this.callBillingUseCase = callBillingUseCase;
        this.contactCrmLinkRepository = contactCrmLinkRepository;
    }

    @Override
    public List<ContactField> findAllByCompanyId(long companyId) {
        return contactFieldRepository.findAllByCompanyId(companyId);
    }

    @Override
    public ContactField create(long companyId, CreateContactFieldRequest request) {
        ContactField field = ContactField.of(request.key().strip(), request.label().strip(), request.type(),
                request.options(), request.description(), request.extractionKey(), request.updateMode());
        ContactFieldValidator.validate(field);
        if (contactFieldRepository.countByCompanyId(companyId) >= ContactFieldValidator.MAX_FIELDS) {
            throw new ValidationException(ErrorCode.CONTACT_FIELDS_TOO_MANY, ContactFieldValidator.MAX_FIELDS);
        }
        if (contactFieldRepository.existsByKey(companyId, field.key())) {
            throw new ConflictException(ErrorCode.CONTACT_FIELD_KEY_EXISTS, field.key());
        }
        long id = contactFieldRepository.create(companyId, field);
        auditUseCase.record(companyId, "CONTACT_FIELD_CREATE", "contact_field", String.valueOf(id), field.key());
        return requireField(companyId, id);
    }

    @Override
    public ContactField update(long companyId, long id, UpdateContactFieldRequest request) {
        ContactField existing = requireField(companyId, id);
        ContactField field = ContactField.of(existing.key(), request.label().strip(), existing.type(),
                request.options(), request.description(), request.extractionKey(), request.updateMode());
        ContactFieldValidator.validate(field);
        contactFieldRepository.update(companyId, id, field);
        auditUseCase.record(companyId, "CONTACT_FIELD_UPDATE", "contact_field", String.valueOf(id), existing.key());
        return requireField(companyId, id);
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        ContactField existing = requireField(companyId, id);
        contactFieldRepository.delete(companyId, id);
        contactRepository.removeCustomFieldKey(companyId, existing.key());
        auditUseCase.record(companyId, "CONTACT_FIELD_DELETE", "contact_field", String.valueOf(id), existing.key());
    }

    /** Not transactional on purpose: an ACCUMULATE merge waits on a model, and no row is held meanwhile. */
    @Override
    public void applyCallValues(long companyId, String phone, ExtractedCallValues values) {
        String normalized = PhoneNumbers.normalize(phone);
        if (normalized == null || values == null || values.isEmpty()) {
            return;
        }
        List<ContactField> fields = contactFieldRepository.findAllByCompanyId(companyId);
        Map<String, ContactField> fieldsByKey = new LinkedHashMap<>();
        fields.forEach(field -> fieldsByKey.put(field.key(), field));

        // The agent's own routing first, so a company mapping of the same key replaces it.
        Map<String, String> incoming = new LinkedHashMap<>(values.byContactField());
        for (ContactField field : fields) {
            String value = field.extractionKey() != null ? values.byExtractionKey().get(field.extractionKey()) : null;
            if (value != null && !value.isBlank()) {
                incoming.put(field.key(), value);
            }
        }
        if (incoming.isEmpty()) {
            return;
        }

        Contact existing = contactRepository.findByPhone(companyId, normalized);
        Map<String, String> customFields = new LinkedHashMap<>(existing != null ? existing.customFields() : Map.of());
        boolean changed = false;
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? null : entry.getValue().strip();
            ContactField field = fieldsByKey.get(key);
            if (!fits(companyId, key, value, field, customFields)) {
                continue;
            }
            ContactFieldUpdateMode mode = field != null ? field.updateMode() : ContactFieldUpdateMode.OVERWRITE;
            String stored = customFields.get(key);
            switch (ContactFieldUpdatePolicy.decide(mode, stored, value)) {
                case SKIP -> {
                    continue;
                }
                case WRITE -> customFields.put(key, value);
                case MERGE -> customFields.put(key, generateMergedValue(companyId, values, field, stored, value));
            }
            changed = true;
        }
        if (!changed) {
            return;
        }
        if (existing == null) {
            contactRepository.create(companyId, Contact.of(normalized, normalized, null, null, null, customFields));
        } else {
            contactRepository.update(companyId, existing.id(),
                    Contact.profile(existing.name(), existing.address(), existing.tags(), existing.notes(), customFields));
            // An imported contact's CRM record is told what the call found (only the mapped fields).
            contactCrmLinkRepository.requestPush(companyId, existing.id(), Instant.now());
        }
    }

    /** Whether the value may be written at all: a usable key and text, its field's type, and room on the contact. */
    private static boolean fits(long companyId, String key, String value, ContactField field,
                                Map<String, String> customFields) {
        if (value == null || value.isEmpty() || !ContactValidator.isValidCustomField(key, value)) {
            log.debug("Dropping invalid contact custom field '{}' for company {}", key, companyId);
            return false;
        }
        if (field != null && !ContactFieldValidator.isValidValue(field, value)) {
            log.debug("Dropping contact field '{}' for company {}: not a valid {}", key, companyId, field.type());
            return false;
        }
        if (!customFields.containsKey(key) && customFields.size() >= ContactValidator.MAX_CUSTOM_FIELDS) {
            log.debug("Dropping contact custom field '{}' for company {}: the contact already has {} fields",
                    key, companyId, ContactValidator.MAX_CUSTOM_FIELDS);
            return false;
        }
        return true;
    }

    /** The model's merge, charged to the call; both values kept one under the other when it gave none. */
    private String generateMergedValue(long companyId, ExtractedCallValues values, ContactField field, String stored,
                               String incoming) {
        MergedFieldValue merged = null;
        try {
            merged = contactFieldMergerPort.generateMergedValue(companyId, values.mergeModel(), field, stored, incoming);
        } catch (Exception e) {
            log.warn("[{}] contact field '{}' merge failed: {}", values.callAttemptId(), field.key(), e.getMessage());
        }
        if (merged == null) {
            return ContactFieldUpdatePolicy.appendBelow(stored, incoming, ContactValidator.MAX_CUSTOM_FIELD_VALUE_LENGTH);
        }
        if (!merged.ownLlm()) {
            try {
                callBillingUseCase.chargeContactFieldMerge(companyId, values.callAttemptId(), field.key(),
                        CallUsage.ofModel(new ModelTokens(merged.model(), merged.promptTokens(),
                                merged.completionTokens(), 0)));
            } catch (Exception e) {
                log.warn("[{}] contact field '{}' merge could not be charged: {}", values.callAttemptId(),
                        field.key(), e.getMessage());
            }
        }
        return ContactFieldUpdatePolicy.fit(merged.value(), ContactValidator.MAX_CUSTOM_FIELD_VALUE_LENGTH);
    }

    private ContactField requireField(long companyId, long id) {
        ContactField field = contactFieldRepository.find(companyId, id);
        if (field == null) {
            throw new NotFoundException(ErrorCode.CONTACT_FIELD_NOT_FOUND, id);
        }
        return field;
    }
}
