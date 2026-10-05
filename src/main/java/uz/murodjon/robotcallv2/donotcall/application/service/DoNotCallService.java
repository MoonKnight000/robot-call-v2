package uz.murodjon.robotcallv2.donotcall.application.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.contact.domain.event.ContactOptOutEvent;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.application.mapper.DoNotCallMapper;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.donotcall.domain.service.DoNotCallValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.*;

/**
 * UseCase implementation for Do-Not-Call (DNC) list operations (§10.8).
 */
@Service
public class DoNotCallService implements DoNotCallUseCase {

    private final DoNotCallRepository doNotCallRepository;
    private final ContactUseCase contactUseCase;
    private final AuditUseCase auditService;
    private final DoNotCallMapper doNotCallMapper;
    private final CurrentUser currentUser;

    public DoNotCallService(DoNotCallRepository doNotCallRepository,
                            ContactUseCase contactUseCase,
                            AuditUseCase auditService,
                            DoNotCallMapper doNotCallMapper,
                            CurrentUser currentUser) {
        this.doNotCallRepository = doNotCallRepository;
        this.contactUseCase = contactUseCase;
        this.auditService = auditService;
        this.doNotCallMapper = doNotCallMapper;
        this.currentUser = currentUser;
    }

    @Override
    public PageableData<DoNotCallRow> list(long companyId, DoNotCallFilter filter) {
        List<DoNotCall> rows = doNotCallRepository.findAll(companyId, filter);
        long total = doNotCallRepository.count(companyId, filter);
        Map<String, String> contactNames = contactUseCase.findNamesByPhones(companyId,
                rows.stream().map(DoNotCall::getPhone).toList());
        List<DoNotCallRow> enriched = rows.stream()
                .map(d -> doNotCallMapper.toRow(d, contactNames.get(d.getPhone())))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public DoNotCallRemoveResponse remove(long companyId, String phone) {
        DoNotCallValidator.validatePhone(phone);
        String actor = currentUser.actorName();
        String normalized = normalizedOrRaw(phone);
        // Entries saved before numbers were normalised are stored as typed — try that too.
        boolean removed = doNotCallRepository.remove(companyId, normalized, actor)
                || (!Objects.equals(normalized, phone) && doNotCallRepository.remove(companyId, phone, actor));
        if (!removed) {
            throw new NotFoundException(ErrorCode.DO_NOT_CALL_ENTRY_NOT_FOUND, phone);
        }
        auditService.record(companyId, "DNC_REMOVE", "do_not_call", phone, null);
        return new DoNotCallRemoveResponse(phone, true);
    }

    @Override
    public void add(long companyId, String phone, String reason, DoNotCallSource source) {
        DoNotCallValidator.validatePhone(phone);
        doNotCallRepository.add(companyId, normalizedOrRaw(phone), reason, source);
    }

    /**
     * A contact asked not to be called again — on the thread that asked, so a number the list
     * refuses fails that request as it did when the contact wrote to the list itself.
     */
    @EventListener
    public void onContactOptOut(ContactOptOutEvent event) {
        add(event.companyId(), event.phone(), event.reason(), DoNotCallSource.MANUAL);
    }

    @Override
    public boolean contains(long companyId, String phone) {
        String normalized = normalizedOrRaw(phone);
        return doNotCallRepository.existsByPhone(companyId, normalized)
                || (!Objects.equals(normalized, phone) && doNotCallRepository.existsByPhone(companyId, phone));
    }

    @Override
    public Set<String> findBlockedPhones(long companyId, Collection<String> phones) {
        // Both spellings of every number, as contains() tries them: entries saved before
        // numbers were normalised are stored as typed.
        Set<String> spellings = new HashSet<>();
        for (String phone : phones) {
            spellings.add(phone);
            spellings.add(normalizedOrRaw(phone));
        }
        Set<String> listed = doNotCallRepository.findListedPhones(companyId, spellings);
        Set<String> blocked = new HashSet<>();
        for (String phone : phones) {
            if (listed.contains(phone) || listed.contains(normalizedOrRaw(phone))) {
                blocked.add(phone);
            }
        }
        return blocked;
    }

    /**
     * One spelling per number, so "+998 90 123-45-67" cannot slip past an entry saved as
     * "998901234567" — the list matched exactly what was typed. Something that does not
     * parse as a number is kept as typed.
     */
    private static String normalizedOrRaw(String phone) {
        String normalized = PhoneNumbers.normalize(phone);
        return normalized != null ? normalized : phone;
    }
}
