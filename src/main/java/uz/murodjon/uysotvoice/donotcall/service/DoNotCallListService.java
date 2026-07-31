package uz.murodjon.uysotvoice.donotcall.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallFilter;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRemoveResponse;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallRow;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;

import java.util.List;

/**
 * The "Qo'ng'iroq qilinmasin (DNC)" tab (§10.8) — listing and removal. Not campaign-
 * scoped, so it does not fit naturally on {@code CampaignController}; kept as its own
 * small service alongside contacts, since the DNC tab lives on the same screen.
 */
@Service
public class DoNotCallListService {

    private final DoNotCallRepository doNotCall;
    private final AuditService audit;

    public DoNotCallListService(DoNotCallRepository doNotCall, AuditService audit) {
        this.doNotCall = doNotCall;
        this.audit = audit;
    }

    public PageableData<DoNotCallRow> list(DoNotCallFilter filter) {
        List<DoNotCallRow> rows = doNotCall.findAll(filter);
        long total = doNotCall.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** "Ro'yxatdan chiqarish" — a phone never opted out here (or already removed) is a 404. */
    public DoNotCallRemoveResponse remove(String phone) {
        boolean removed = doNotCall.remove(phone, currentActor());
        if (!removed) {
            throw new NotFoundException("No active do-not-call entry for " + phone);
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
