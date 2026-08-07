package uz.murodjon.uysotvoice.donotcall.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.contact.service.ContactService;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallFilter;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRemoveResponse;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCall;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRow;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;

import java.util.List;
import java.util.Map;

/**
 * The "Qo'ng'iroq qilinmasin (DNC)" tab (§10.8) — listing and removal. Not campaign-
 * scoped, so it does not fit naturally on {@code CampaignController}; kept as its own
 * small service alongside contacts, since the DNC tab lives on the same screen.
 */
@Service
public class DoNotCallListService {

    private final DoNotCallRepository doNotCall;
    private final ContactService contacts;
    private final AuditService audit;

    public DoNotCallListService(DoNotCallRepository doNotCall, ContactService contacts, AuditService audit) {
        this.doNotCall = doNotCall;
        this.contacts = contacts;
        this.audit = audit;
    }

    /**
     * Enriches each row with {@code contactName} (backend-uchun-talablar.md §15) via a
     * batched phone→name lookup rather than one query per row.
     */
    public PageableData<DoNotCallRow> list(DoNotCallFilter filter) {
        List<DoNotCall> rows = doNotCall.findAll(filter);
        long total = doNotCall.count(filter);
        Map<String, String> contactNames = contacts.namesByPhones(rows.stream().map(DoNotCall::phone).toList());
        List<DoNotCallRow> enriched = rows.stream()
                .map(d -> DoNotCallRow.of(d, contactNames.get(d.phone())))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** "Ro'yxatdan chiqarish" — a phone never opted out here (or already removed) is a 404. */
    public DoNotCallRemoveResponse remove(String phone) {
        boolean removed = doNotCall.remove(phone, currentActor());
        if (!removed) {
            throw new NotFoundException(ErrorCode.DO_NOT_CALL_ENTRY_NOT_FOUND, phone);
        }
        audit.record("DNC_REMOVE", "do_not_call", phone, null);
        return new DoNotCallRemoveResponse(phone, true);
    }

    /** Same "who did it" the audit trail records — see {@code AuditService.currentActor}. */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null || auth.getName().isBlank() ? "system" : auth.getName();
    }
}
