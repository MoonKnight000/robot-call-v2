package uz.murodjon.robotcallv2.contact.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.ContactDncResponse;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Contact CRUD, CSV import, and the DNC hand-off (§10.8).
 */
@Service
public class ContactService implements ContactUseCase {

    private static final int CALL_HISTORY_LIMIT = 50;

    private final ContactRepository contactRepository;
    private final ReportRepository reportRepository;
    private final DoNotCallRepository doNotCallRepository;
    private final AuditService auditService;

    public ContactService(ContactRepository contactRepository,
                          ReportRepository reportRepository,
                          DoNotCallRepository doNotCallRepository,
                          AuditService auditService) {
        this.contactRepository = contactRepository;
        this.reportRepository = reportRepository;
        this.doNotCallRepository = doNotCallRepository;
        this.auditService = auditService;
    }

    @Override
    public Contact create(long companyId, CreateContactRequest request) {
        String phone = PhoneNumbers.require(request.phone());
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
        return PageableData.of(contactRepository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                contactRepository.count(companyId, filter));
    }

    @Override
    public ContactDetail detail(long companyId, long id) {
        Contact contact = requireContact(companyId, id);
        List<ContactCallHistoryRow> timeline =
                reportRepository.callsForPhone(companyId, contact.phone(), CALL_HISTORY_LIMIT);
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
    public Map<String, String> namesByPhones(long companyId, Collection<String> phones) {
        return contactRepository.namesByPhones(companyId, phones);
    }

    @Override
    public ContactImportResult importCsv(long companyId, String csv) {
        ContactCsvParseResult parsed = ContactCsvImporter.parse(csv);
        List<Long> addedIds = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());

        for (ParsedContact c : parsed.contacts()) {
            try {
                String phone = PhoneNumbers.require(c.phone());
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
        doNotCallRepository.add(companyId, contact.phone(), "opted out via contact", DoNotCallSource.MANUAL);
        auditService.record(companyId, "CONTACT_DNC", "contact", String.valueOf(id), contact.phone());
        return new ContactDncResponse(id, true);
    }
}

