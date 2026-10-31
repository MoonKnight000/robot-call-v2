package uz.murodjon.robotcallv2.siptrunk.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.*;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

public interface SipTrunkUseCase {

    SipTrunkRow create(long companyId, CreateSipTrunkRequest request);

    SipTrunkRow update(long companyId, long id, UpdateSipTrunkRequest request);

    SipTrunkRow makeDefault(long companyId, long id);

    SipTrunkDeleteResponse delete(long companyId, long id);

    PageableData<SipTrunkRow> list(long companyId, SipTrunkFilter filter);

    SipTrunkRow requireTrunk(long companyId, long id);

    SipTrunkCredentials requireCredentialsForCall(long companyId, long id);

    SipTrunkRow findDefaultForCall(long companyId);

    List<SipTrunkRow> findAllEnabledForCompany(long companyId);

    SipTrunkStatus findStatus(long companyId, long id);

    List<SipTrunkStatus> findAllStatuses(long companyId);
}
