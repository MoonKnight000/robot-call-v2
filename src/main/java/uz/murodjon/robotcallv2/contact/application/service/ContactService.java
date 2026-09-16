package uz.murodjon.robotcallv2.contact.application.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.OutboxMessageUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactPrivacyRepository;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.application.mapper.ContactMapper;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.contact.domain.event.ContactOptOutEvent;
import uz.murodjon.robotcallv2.contact.domain.service.ContactValidator;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.shared.util.Tokens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contact CRUD, CSV import, and the DNC hand-off (§10.8).
 */
@Service
public class ContactService implements ContactUseCase {

    private static final int CALL_HISTORY_LIMIT = 50;

    private final ContactRepository contactRepository;
    /**
     * Resolved on first use, not at start-up: DoNotCallService depends on this service, and
     * so, through CampaignService, does ReportService.
     */
    private final ObjectProvider<ReportUseCase> reportUseCaseProvider;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AuditUseCase auditService;
    private final ContactPrivacyRepository privacyRepository;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final CallTranscriptUseCase callTranscriptUseCase;
    /** Where the call records' undelivered CRM writes are emptied on an erasure. */
    private final OutboxMessageUseCase outboxMessageUseCase;
    private final FileStorageUseCase fileStorageService;
    private final ContactMapper contactMapper;

    public ContactService(ContactRepository contactRepository,
                          ObjectProvider<ReportUseCase> reportUseCaseProvider,
                          ApplicationEventPublisher applicationEventPublisher,
                          AuditUseCase auditService,
                          ContactPrivacyRepository privacyRepository,
                          ClientMemoryUseCase clientMemoryUseCase,
                          CallTranscriptUseCase callTranscriptUseCase,
                          OutboxMessageUseCase outboxMessageUseCase,
                          FileStorageUseCase fileStorageService,
                          ContactMapper contactMapper) {
        this.privacyRepository = privacyRepository;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.outboxMessageUseCase = outboxMessageUseCase;
        this.fileStorageService = fileStorageService;
        this.contactRepository = contactRepository;
        this.reportUseCaseProvider = reportUseCaseProvider;
        this.applicationEventPublisher = applicationEventPublisher;
        this.auditService = auditService;
        this.contactMapper = contactMapper;
    }

    @Override
    public Contact create(long companyId, CreateContactRequest request) {
        String phone = PhoneNumbers.require(request.phone());
        ContactValidator.validatePhone(phone);
        if (contactRepository.existsByPhone(companyId, phone)) {
            throw new ConflictException(ErrorCode.CONTACT_PHONE_EXISTS, phone);
        }
        long id = contactRepository.create(companyId, Contact.of(request.name(), phone, request.address(), request.tags(), request.notes()));
        auditService.record(companyId, "CONTACT_CREATE", "contact", String.valueOf(id), phone);
        return requireContact(companyId, id);
    }

    @Override
    public Contact update(long companyId, long id, UpdateContactRequest request) {
        Contact existing = requireContact(companyId, id);
        contactRepository.update(companyId, id, Contact.profile(request.name(), request.address(), request.tags(), request.notes()));
        auditService.record(companyId, "CONTACT_UPDATE", "contact", String.valueOf(id), existing.phone());
        return requireContact(companyId, id);
    }

    @Override
    public void delete(long companyId, long id) {
        Contact existing = requireContact(companyId, id);
        contactRepository.delete(companyId, id);
        auditService.record(companyId, "CONTACT_DELETE", "contact", String.valueOf(id), existing.phone());
    }

    @Override
    public PageableData<Contact> list(long companyId, ContactFilter filter) {
        return contactRepository.findPage(companyId, filter);
    }

    @Override
    public ContactDetail requireContactDetail(long companyId, long id) {
        Contact contact = requireContact(companyId, id);
        List<ContactCallHistoryRow> timeline =
                reportUseCaseProvider.getObject().findCallsForPhone(companyId, contact.phone(), CALL_HISTORY_LIMIT).stream()
                        .map(contactMapper::toContactCallHistoryRow)
                        .toList();
        return new ContactDetail(contact, timeline);
    }

    @Override
    public Contact requireContact(long companyId, long id) {
        Contact contact = contactRepository.find(companyId, id);
        if (contact == null) {
            throw new NotFoundException(ErrorCode.CONTACT_NOT_FOUND, id);
        }
        return contact;
    }

    @Override
    public Map<String, String> findNamesByPhones(long companyId, Collection<String> phones) {
        return contactRepository.findNamesByPhones(companyId, phones);
    }

    @Override
    public ContactImportResult importCsv(long companyId, String csv) {
        ContactCsvParseResult parsed = ContactCsvImporter.parse(csv);
        List<Long> addedIds = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());

        for (ParsedContact c : parsed.contacts()) {
            try {
                String phone = PhoneNumbers.require(c.phone());
                ContactValidator.validatePhone(phone);
                if (contactRepository.existsByPhone(companyId, phone)) {
                    errors.add(new CsvRowError(c.line(), "phone " + phone + " already exists"));
                    continue;
                }
                long id = contactRepository.create(companyId, Contact.of(c.name(), phone, c.address(), c.tags(), c.notes()));
                addedIds.add(id);
            } catch (Exception e) {
                errors.add(new CsvRowError(c.line(), e.getMessage()));
            }
        }
        auditService.record(companyId, "CONTACTS_IMPORT", "contact", null, addedIds.size() + " added, " + errors.size() + " rejected");
        return new ContactImportResult(addedIds.size(), addedIds, errors, parsed.unknownColumns());
    }

    @Override
    public ContactDncResponse addToDoNotCall(long companyId, long id) {
        Contact contact = requireContact(companyId, id);
        applicationEventPublisher.publishEvent(new ContactOptOutEvent(companyId, contact.phone(), "opted out via contact"));
        auditService.record(companyId, "CONTACT_DNC", "contact", String.valueOf(id), contact.phone());
        return new ContactDncResponse(id, true);
    }

    @Override
    public ContactDataExport exportSubjectData(long companyId, String phone) {
        List<String> phones = phoneSpellings(phone);
        Contact contact = phones.stream().map(p -> contactRepository.findByPhone(companyId, p))
                .filter(Objects::nonNull).findFirst().orElse(null);
        ClientMemory memory = phones.stream().map(p -> clientMemoryUseCase.findByCompanyIdAndPhone(companyId, p))
                .filter(Objects::nonNull).findFirst().orElse(null);
        List<ContactDataExportCall> calls = privacyRepository.findCalls(companyId, phones).stream()
                .map(call -> new ContactDataExportCall(call.callId(), call.startedAt(), call.durationSec(),
                        call.disposition(), call.summary(), callTranscriptUseCase.findTranscriptText(call.callId())))
                .toList();
        auditService.record(companyId, "SUBJECT_EXPORTED", "contact", PhoneMasker.mask(phones.get(0)),
                calls.size() + " calls");
        return new ContactDataExport(phones.get(0), contact, memory, calls);
    }

    /**
     * Files are removed after the rows: the transaction that anonymizes the calls must not
     * wait on a bucket, and a file whose delete failed is still findable through its row.
     */
    @Override
    public ContactErasureResponse eraseSubjectData(long companyId, String phone, boolean confirm) {
        if (!confirm) {
            throw new ValidationException(ErrorCode.SUBJECT_ERASURE_NOT_CONFIRMED);
        }
        List<String> phones = phoneSpellings(phone);
        // Before the rows: the messages are found through the calls' numbers, which the erase replaces.
        outboxMessageUseCase.eraseMessagesOfPhones(companyId, phones);
        List<Long> fileIds = privacyRepository.findFileIds(companyId, phones);
        int calls = privacyRepository.erase(companyId, phones, erasedPhone(companyId, phones.get(0)));
        int filesDeleted = 0;
        for (long fileId : fileIds) {
            if (fileStorageService.delete(companyId, fileId)) {
                filesDeleted++;
            }
        }
        String masked = PhoneMasker.mask(phones.get(0));
        auditService.record(companyId, "SUBJECT_ERASED", "contact", masked,
                calls + " calls, " + filesDeleted + "/" + fileIds.size() + " files");
        return new ContactErasureResponse(masked, calls, filesDeleted);
    }

    /** Every spelling the number may be stored under: with and without the leading plus. */
    private static List<String> phoneSpellings(String phone) {
        String normalized = PhoneNumbers.require(phone);
        return normalized.startsWith("+")
                ? List.of(normalized, normalized.substring(1))
                : List.of(normalized, "+" + normalized);
    }

    /**
     * What an erased number becomes in the rows kept for reporting: stable per company and
     * number, so the calls still group together, and not a number anything can dial.
     */
    private static String erasedPhone(long companyId, String phone) {
        return "x" + Tokens.hash(companyId + ":" + phone).substring(0, 16);
    }
}
