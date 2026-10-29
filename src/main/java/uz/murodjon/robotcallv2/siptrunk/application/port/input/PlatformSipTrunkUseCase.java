package uz.murodjon.robotcallv2.siptrunk.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CreateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkDeleteResponse;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkStatus;
import uz.murodjon.robotcallv2.siptrunk.application.dto.UpdateSipTrunkRequest;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;

import java.util.List;

/** Platform staff setting up the SIP trunks of any company — the company id comes from the URL, not from the token. */
public interface PlatformSipTrunkUseCase {

    SipTrunkRow create(long companyId, CreateSipTrunkRequest request);

    PageableData<SipTrunkRow> list(long companyId, SipTrunkFilter filter);

    SipTrunkRow requireTrunk(long companyId, long id);

    SipTrunkRow update(long companyId, long id, UpdateSipTrunkRequest request);

    SipTrunkRow makeDefault(long companyId, long id);

    SipTrunkDeleteResponse delete(long companyId, long id);

    List<SipTrunkStatus> findAllStatuses(long companyId);
}
