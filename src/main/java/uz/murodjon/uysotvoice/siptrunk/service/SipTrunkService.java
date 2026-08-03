package uz.murodjon.uysotvoice.siptrunk.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.siptrunk.dto.CreateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkFilter;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunk;
import uz.murodjon.uysotvoice.siptrunk.dto.UpdateSipTrunkRequest;
import uz.murodjon.uysotvoice.siptrunk.repository.SipTrunkRepository;

import java.util.List;

/**
 * SIP trunk CRUD (ROADMAP B.3): each company may register several outbound PJSIP
 * trunks (this project does not manage {@code pjsip.conf} itself — an endpoint must
 * already be configured in Asterisk before it is registered here), exactly one of
 * which is the company's default. {@link #findDefaultForCall} is the runtime hot path
 * {@code AriService} calls when originating a call — it must never throw, only the
 * admin CRUD methods below it do.
 */
@Service
public class SipTrunkService {

    private final SipTrunkRepository repo;
    private final AuditService audit;

    public SipTrunkService(SipTrunkRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public SipTrunk create(CreateSipTrunkRequest r) {
        long id = repo.create(r.name(), r.pjsipEndpoint(), r.callerId(), false);
        audit.record("SIP_TRUNK_CREATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(id);
    }

    public SipTrunk update(long id, UpdateSipTrunkRequest r) {
        requireTrunk(id);
        repo.update(id, r.name(), r.pjsipEndpoint(), r.callerId(), r.enabled());
        audit.record("SIP_TRUNK_UPDATE", "sip_trunk", String.valueOf(id), r.name());
        return requireTrunk(id);
    }

    /** Promotes {@code id} to the company's default trunk, demoting whichever one held it before. */
    public SipTrunk makeDefault(long id) {
        requireTrunk(id);
        repo.makeDefault(id);
        audit.record("SIP_TRUNK_SET_DEFAULT", "sip_trunk", String.valueOf(id), null);
        return requireTrunk(id);
    }

    /**
     * The default trunk cannot be deleted directly — promote another one first, so a
     * company is never left with calls silently falling back to the global config
     * without anyone having decided that on purpose.
     */
    public void delete(long id) {
        SipTrunk trunk = requireTrunk(id);
        if (trunk.isDefault()) {
            throw new ConflictException(
                    "Cannot delete the default trunk — set another one as default first");
        }
        repo.delete(id);
        audit.record("SIP_TRUNK_DELETE", "sip_trunk", String.valueOf(id), trunk.name());
    }

    public PageableData<SipTrunk> list(SipTrunkFilter filter) {
        List<SipTrunk> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** As {@link SipTrunkRepository#find}, for the REST API — a missing trunk is a 404, not a null. */
    public SipTrunk requireTrunk(long id) {
        SipTrunk row = repo.find(id);
        if (row == null) {
            throw new NotFoundException("sip_trunk", id);
        }
        return row;
    }

    /**
     * {@code companyId}'s enabled default trunk, or {@code null} if it has none —
     * called from {@code AriService} at call-origination time, on the call thread, so
     * this never throws; the caller falls back to the globally configured trunk.
     */
    public SipTrunk findDefaultForCall(long companyId) {
        return repo.findDefaultForCompany(companyId);
    }
}
