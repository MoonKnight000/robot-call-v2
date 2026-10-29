package uz.murodjon.robotcallv2.phonenumber.application.port.input;

import uz.murodjon.robotcallv2.phonenumber.application.dto.*;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface PhoneNumberUseCase {

    PageableData<PhoneNumberRow> findMarketplaceNumbers(PhoneNumberFilter filter);

    PhoneNumberRow purchaseNumber(long companyId, PurchaseNumberRequest request);

    PageableData<PhoneNumberRow> findCompanyNumbers(long companyId, PhoneNumberFilter filter);

    PhoneNumberRow updateInboundRouting(long companyId, long id, UpdateInboundRoutingRequest request);

    void releaseNumber(long companyId, long id);

    TestSipConnectionResponse testSipConnection(TestSipConnectionRequest request);
}
