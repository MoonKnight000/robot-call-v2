package uz.murodjon.robotcallv2.contact.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactCrmImportUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactCrmLinkRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldRepository;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactCrmLink;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.ImportedCrmContact;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactImportOutcome;
import uz.murodjon.robotcallv2.contact.domain.service.ContactFieldValidator;
import uz.murodjon.robotcallv2.contact.domain.service.ContactValidator;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class ContactCrmImportService implements ContactCrmImportUseCase {

    private static final Logger log = LoggerFactory.getLogger(ContactCrmImportService.class);
    private static final int MAX_NAME_LENGTH = 255;

    private final ContactRepository contactRepository;
    private final ContactFieldRepository contactFieldRepository;
    private final ContactCrmLinkRepository contactCrmLinkRepository;

    public ContactCrmImportService(ContactRepository contactRepository, ContactFieldRepository contactFieldRepository,
                                   ContactCrmLinkRepository contactCrmLinkRepository) {
        this.contactRepository = contactRepository;
        this.contactFieldRepository = contactFieldRepository;
        this.contactCrmLinkRepository = contactCrmLinkRepository;
    }

    @Override
    public ContactImportOutcome importCrmContact(long companyId, ImportedCrmContact imported) {
        String phone = PhoneNumbers.normalize(imported.phone());
        if (phone == null || imported.externalId() == null || imported.externalId().isBlank()) {
            return ContactImportOutcome.SKIPPED;
        }
        Long linkedId = contactCrmLinkRepository.findContactId(companyId, imported.provider(), imported.externalId());
        Contact existing = linkedId != null ? contactRepository.find(companyId, linkedId) : null;
        if (existing == null) {
            existing = contactRepository.findByPhone(companyId, phone);
        }

        Map<String, ContactField> fields = new LinkedHashMap<>();
        contactFieldRepository.findAllByCompanyId(companyId).forEach(field -> fields.put(field.key(), field));
        Map<String, String> customFields = new LinkedHashMap<>(existing != null ? existing.customFields() : Map.of());
        imported.customFields().forEach((key, raw) -> {
            String value = raw == null ? null : raw.strip();
            if (fits(key, value, fields.get(key), customFields)) {
                customFields.put(key, value);
            } else if (value != null && !value.isEmpty()) {
                log.debug("CRM import for company {}: dropping value of '{}'", companyId, key);
            }
        });
        String name = imported.name() == null || imported.name().isBlank()
                ? (existing != null ? existing.name() : phone)
                : truncate(imported.name().strip());

        long contactId;
        ContactImportOutcome outcome;
        if (existing == null) {
            contactId = contactRepository.create(companyId, Contact.of(name, phone, null, null, null, customFields));
            outcome = ContactImportOutcome.CREATED;
        } else {
            contactId = existing.id();
            boolean same = Objects.equals(existing.name(), name) && existing.customFields().equals(customFields);
            if (!same) {
                contactRepository.update(companyId, contactId, Contact.profile(name, existing.address(), existing.tags(),
                        existing.notes(), customFields));
            }
            outcome = same && Objects.equals(linkedId, contactId) ? ContactImportOutcome.UNCHANGED : ContactImportOutcome.UPDATED;
        }
        contactCrmLinkRepository.upsert(companyId,
                new ContactCrmLink(contactId, imported.provider(), imported.externalId(), Instant.now()));
        return outcome;
    }

    private static boolean fits(String key, String value, ContactField field, Map<String, String> customFields) {
        if (value == null || value.isEmpty() || !ContactValidator.isValidCustomField(key, value)) {
            return false;
        }
        if (field != null && !ContactFieldValidator.isValidValue(field, value)) {
            return false;
        }
        return customFields.containsKey(key) || customFields.size() < ContactValidator.MAX_CUSTOM_FIELDS;
    }

    private static String truncate(String name) {
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
