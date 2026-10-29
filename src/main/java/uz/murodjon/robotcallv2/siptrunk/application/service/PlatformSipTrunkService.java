package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CreateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkDeleteResponse;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkStatus;
import uz.murodjon.robotcallv2.siptrunk.application.dto.UpdateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.PlatformSipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

/**
 * The trunk operations a company's own admin has, aimed at a company chosen by the platform.
 * {@link SipTrunkService} already takes the company as an argument and lets a
 * {@code PLATFORM_ADMIN} caller point a trunk at a hand-written PJSIP endpoint, so this only
 * adds the one thing it cannot know: that the named company exists.
 */
@Service
public class PlatformSipTrunkService implements PlatformSipTrunkUseCase {

    private final CompanyUseCase companyUseCase;
    private final SipTrunkUseCase sipTrunkUseCase;

    public PlatformSipTrunkService(CompanyUseCase companyUseCase, SipTrunkUseCase sipTrunkUseCase) {
        this.companyUseCase = companyUseCase;
        this.sipTrunkUseCase = sipTrunkUseCase;
    }

    @Override
    public SipTrunkRow create(long companyId, CreateSipTrunkRequest request) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.create(companyId, request);
    }

    @Override
    public PageableData<SipTrunkRow> list(long companyId, SipTrunkFilter filter) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.list(companyId, filter);
    }

    @Override
    public SipTrunkRow requireTrunk(long companyId, long id) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.requireTrunk(companyId, id);
    }

    @Override
    public SipTrunkRow update(long companyId, long id, UpdateSipTrunkRequest request) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.update(companyId, id, request);
    }

    @Override
    public SipTrunkRow makeDefault(long companyId, long id) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.makeDefault(companyId, id);
    }

    @Override
    public SipTrunkDeleteResponse delete(long companyId, long id) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.delete(companyId, id);
    }

    @Override
    public List<SipTrunkStatus> findAllStatuses(long companyId) {
        companyUseCase.requireExists(companyId);
        return sipTrunkUseCase.findAllStatuses(companyId);
    }
}
