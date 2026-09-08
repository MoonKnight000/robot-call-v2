package uz.murodjon.robotcallv2.donotcall.application.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.contact.application.service.ContactService;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.application.mapper.DoNotCallMapper;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCall;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.List;
import java.util.Map;

/**
 * UseCase implementation for Do-Not-Call (DNC) list operations (§10.8).
 */
@Service
public class DoNotCallService implements DoNotCallUseCase {

    private final DoNotCallRepository doNotCallRepository;
    private final ContactService contactService;
    private final AuditService auditService;
    private final DoNotCallMapper doNotCallMapper;

    public DoNotCallService(DoNotCallRepository doNotCallRepository,
                            ContactService contactService,
                            AuditService auditService,
                            DoNotCallMapper doNotCallMapper) {
        this.doNotCallRepository = doNotCallRepository;
        this.contactService = contactService;
        this.auditService = auditService;
        this.doNotCallMapper = doNotCallMapper;
    }

    @Override
    public PageableData<DoNotCallRow> list(long companyId, DoNotCallFilter filter) {
        List<DoNotCall> rows = doNotCallRepository.findAll(companyId, filter);
        long total = doNotCallRepository.count(companyId, filter);
        Map<String, String> contactNames = contactService.namesByPhones(companyId,
                rows.stream().map(DoNotCall::getPhone).toList());
        List<DoNotCallRow> enriched = rows.stream()
                .map(d -> doNotCallMapper.domainToRow(d, contactNames.get(d.getPhone())))
                .toList();
        return PageableData.of(enriched, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public DoNotCallRemoveResponse remove(long companyId, String phone) {
        boolean removed = doNotCallRepository.remove(companyId, phone, currentActor());
        if (!removed) {
            throw new NotFoundException(ErrorCode.DO_NOT_CALL_ENTRY_NOT_FOUND, phone);
        }
        auditService.record(companyId, "DNC_REMOVE", "do_not_call", phone, null);
        return new DoNotCallRemoveResponse(phone, true);
    }

    @Override
    public void add(long companyId, String phone, String reason, DoNotCallSource source) {
        doNotCallRepository.add(companyId, phone, reason, source);
    }

    @Override
    public boolean contains(long companyId, String phone) {
        return doNotCallRepository.contains(companyId, phone);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null || auth.getName().isBlank() ? "system" : auth.getName();
    }
}
