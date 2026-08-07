package uz.murodjon.uysotvoice.contact.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.contact.dto.ContactCallHistoryRow;
import uz.murodjon.uysotvoice.contact.dto.ContactCsvParseResult;
import uz.murodjon.uysotvoice.contact.dto.ContactDetail;
import uz.murodjon.uysotvoice.contact.dto.ContactFilter;
import uz.murodjon.uysotvoice.contact.dto.ContactImportResult;
import uz.murodjon.uysotvoice.contact.dto.Contact;
import uz.murodjon.uysotvoice.contact.dto.CreateContactRequest;
import uz.murodjon.uysotvoice.contact.dto.ParsedContact;
import uz.murodjon.uysotvoice.contact.dto.UpdateContactRequest;
import uz.murodjon.uysotvoice.contact.repository.ContactRepository;
import uz.murodjon.uysotvoice.donotcall.dto.ContactDncResponse;
import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.report.repository.ReportRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.csv.CsvRowError;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.util.PhoneNumbers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Contact CRUD, CSV import, and the DNC hand-off (§10.8). Not in ROADMAP — designed
 * fresh for the panel: {@code contact} is independent of {@code campaign_target},
 * which stays transient/campaign-scoped; the call-history timeline is matched by
 * phone number (see {@link ReportRepository#callsForPhone}), the same precedent
 * {@code do_not_call_list} already set for surviving across campaigns.
 */
@Service
public class ContactService {

    /** Recent calls shown in the drawer timeline — a summary, not a paginated report. */
    private static final int CALL_HISTORY_LIMIT = 50;

    private final ContactRepository contacts;
    private final ReportRepository reports;
    private final DoNotCallRepository doNotCall;
    private final AuditService audit;

    public ContactService(ContactRepository contacts, ReportRepository reports,
                          DoNotCallRepository doNotCall, AuditService audit) {
        this.contacts = contacts;
        this.reports = reports;
        this.doNotCall = doNotCall;
        this.audit = audit;
    }

    public Contact create(CreateContactRequest r) {
        String phone = PhoneNumbers.require(r.phone());
        if (contacts.existsByPhone(phone)) {
            throw new ConflictException(ErrorCode.CONTACT_PHONE_EXISTS, phone);
        }
        long id = contacts.create(r.name(), phone, r.address(), r.tags(), r.notes());
        audit.record("CONTACT_CREATE", "contact", String.valueOf(id), phone);
        return contacts.find(id);
    }

    public Contact update(long id, UpdateContactRequest r) {
        requireContact(id);
        contacts.update(id, r.name(), r.address(), r.tags(), r.notes());
        audit.record("CONTACT_UPDATE", "contact", String.valueOf(id), null);
        return requireContact(id);
    }

    public PageableData<Contact> list(ContactFilter filter) {
        List<Contact> rows = contacts.findAll(filter);
        long total = contacts.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** As {@link ContactRepository#find}, for the REST API — a missing contact is a 404, not a null. */
    public Contact requireContact(long id) {
        Contact row = contacts.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.CONTACT_NOT_FOUND, id);
        }
        return row;
    }

    /**
     * Cheap phone→name lookup for other features to enrich rows with a contact name
     * (e.g. {@code DoNotCallRow}, backend-uchun-talablar.md §15).
     */
    public Map<String, String> namesByPhones(Collection<String> phones) {
        return contacts.namesByPhones(phones);
    }

    /** Profile + call-history timeline (§10.8 drawer). */
    public ContactDetail detail(long id) {
        Contact contact = requireContact(id);
        List<ContactCallHistoryRow> history = reports.callsForPhone(contact.phone(), CALL_HISTORY_LIMIT);
        return new ContactDetail(contact, history);
    }

    /**
     * Bulk-load contacts from a CSV export (§10.8). A row that cannot be used is
     * reported and skipped rather than failing the file, matching {@code
     * CampaignService.importTargetsCsv}'s handling of {@code TargetCsvImporter}.
     */
    public ContactImportResult importCsv(String csv) {
        ContactCsvParseResult parsed = ContactCsvImporter.parse(csv);
        List<Long> added = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());
        for (ParsedContact c : parsed.contacts()) {
            try {
                String phone = PhoneNumbers.require(c.phone());
                if (contacts.existsByPhone(phone)) {
                    errors.add(new CsvRowError(c.line(), "phone " + phone + " already exists"));
                    continue;
                }
                added.add(contacts.create(c.name(), phone, c.address(), c.tags(), c.notes()));
            } catch (Exception e) {
                // Almost always an unusable phone number or a duplicate — the one error
                // worth naming per row.
                errors.add(new CsvRowError(c.line(), e.getMessage()));
            }
        }
        audit.record("CONTACTS_IMPORT", "contact", null, added.size() + " added, " + errors.size() + " rejected");
        return new ContactImportResult(added.size(), added, errors, parsed.unknownColumns());
    }

    /** "DNC ga qo'shish" (§10.8 drawer) — adds the contact's phone to the opt-out list. */
    public ContactDncResponse addToDoNotCall(long id) {
        Contact contact = requireContact(id);
        doNotCall.add(contact.phone(), "opted out via contact", DoNotCallSource.MANUAL);
        audit.record("CONTACT_DNC", "contact", String.valueOf(id), contact.phone());
        return new ContactDncResponse(id, true);
    }
}
