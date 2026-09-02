package uz.murodjon.robotcallv2.siptrunk.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;

import java.util.Collection;
import java.util.List;

public interface SipTrunkUseCase {

    SipTrunkRow create(CreateSipTrunkRequest r);

    SipTrunkRow update(long id, UpdateSipTrunkRequest r);

    SipTrunkRow makeDefault(long id);

    void delete(long id);

    PageableData<SipTrunkRow> list(SipTrunkFilter filter);

    SipTrunkRow requireTrunk(long id);

    SipTrunkRow findDefaultForCall(long companyId);

    List<SipTrunkRow> findTrunksForCall(long companyId, Collection<Long> candidateTrunkIds);

    List<SipTrunkRow> findAllEnabledForCompany(long companyId);

    SipTrunkStatus getStatus(long id);

    List<SipTrunkStatus> getAllStatuses();
}
