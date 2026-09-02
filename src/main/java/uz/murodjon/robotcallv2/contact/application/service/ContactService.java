package uz.murodjon.robotcallv2.contact.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.contact.application.dto.*;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactRepository;
import uz.murodjon.robotcallv2.contact.domain.entity.Contact;
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
    private final CurrentCompany currentCompany;

    public ContactService(ContactRepository contactRepository,
                          ReportRepository reportRepository,
                          DoNotCallRepository doNotCallRepository,
                          AuditService auditService,
                          CurrentCompany currentCompany) {
        this.contactRepository = contactRepository;
        this.reportRepository = reportRepository;
        this.doNotCallRepository = doNotCallRepository;
        this.auditService = auditService;
        this.currentCompany = currentCompany;
    }

    @Override
    public Contact create(CreateContactRequest r) {
        long companyId = currentCompany.id();
        String phone = PhoneNumbers.require(r.phone());
        if (contactRepository.existsByPhone(companyId, phone)) {
            throw new ConflictException(ErrorCode.CONTACT_PHONE_EXISTS, phone);
        }
        long id = contactRepository.create(companyId, r.name(), phone, r.address(), r.tags(), r.notes());
        auditService.record("CONTACT_CREATE", "contact", String.valueOf(id), phone);
        return requireContact(id);
    }

    @Override
    public Contact update(long id, UpdateContactRequest r) {
        long companyId = currentCompany.id();
        Contact existing = requireContact(id);
        contactRepository.update(companyId, id, r.name(), r.address(), r.tags(), r.notes());
        auditService.record("CONTACT_UPDATE", "contact", String.valueOf(id), existing.phone());
        return requireContact(id);
    }

    @Override
    public void delete(long id) {
        long companyId = currentCompany.id();
        Contact existing = requireContact(id);
        contactRepository.delete(companyId, id);
        auditService.record("CONTACT_DELETE", "contact", String.valueOf(id), existing.phone());
    }

    @Override
    public PageableData<Contact> list(ContactFilter filter) {
        long companyId = currentCompany.id();
        return PageableData.of(contactRepository.findAll(companyId, filter), filter.pageOrDefault(), filter.sizeOrDefault(),
                contactRepository.count(companyId, filter));
    }

    @Override
    public ContactDetail detail(long id) {
        Contact contact = requireContact(id);
        List<ContactCallHistoryRow> timeline = reportRepository.callsForPhone(contact.phone(), CALL_HISTORY_LIMIT);
        return new ContactDetail(contact, timeline);
    }

    @Override
    public Contact requireContact(long id) {
        long companyId = currentCompany.id();
        Contact contact = contactRepository.find(companyId, id);
        if (contact == null) {
            throw new NotFoundException(ErrorCode.CONTACT_NOT_FOUND, id);
        }
        return contact;
    }

    @Override
    public Map<String, String> namesByPhones(Collection<String> phones) {
        return contactRepository.namesByPhones(currentCompany.id(), phones);
    }

    @Override
    public ContactImportResult importCsv(String csv) {
        long companyId = currentCompany.id();
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
                long id = contactRepository.create(companyId, c.name(), phone, c.address(), c.tags(), c.notes());
                addedIds.add(id);
            } catch (Exception e) {
                errors.add(new CsvRowError(c.line(), e.getMessage()));
            }
        }
        auditService.record("CONTACTS_IMPORT", "contact", null, addedIds.size() + " added, " + errors.size() + " rejected");
        return new ContactImportResult(addedIds.size(), addedIds, errors, parsed.unknownColumns());
    }

    @Override
    public ContactDncResponse addToDoNotCall(long id) {
        long companyId = currentCompany.id();
        Contact contact = requireContact(id);
        doNotCallRepository.add(companyId, contact.phone(), "opted out via contact", DoNotCallSource.MANUAL);
        auditService.record("CONTACT_DNC", "contact", String.valueOf(id), contact.phone());
        return new ContactDncResponse(id, true);
    }
}

