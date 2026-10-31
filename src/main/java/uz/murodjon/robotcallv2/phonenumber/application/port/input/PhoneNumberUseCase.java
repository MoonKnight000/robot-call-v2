package uz.murodjon.robotcallv2.phonenumber.application.port.input;

import uz.murodjon.robotcallv2.phonenumber.application.dto.*;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface PhoneNumberUseCase {

    PageableData<PhoneNumberRow> findMarketplaceNumbers(PhoneNumberFilter filter);

    PhoneNumberRow purchaseNumber(long companyId, PurchaseNumberRequest request);

    PhoneNumberRow createByoNumber(long companyId, CreateByoNumberRequest request);

    PageableData<PhoneNumberRow> findCompanyNumbers(long companyId, PhoneNumberFilter filter);

    PhoneNumberRow updateTrunk(long companyId, long id, UpdatePhoneNumberTrunkRequest request);

    /** The company's number {@code id}; null when there is none, or it is another company's or back in the pool. */
    PhoneNumber findCompanyNumber(long companyId, long id);

    /**
     * The number a call to {@code did} reached, if a company holds it — for an inbound call, whose
     * company is not known until the number is found. Null for a number nobody holds.
     */
    PhoneNumber findAssignedByPhoneNumber(String did);

    PhoneNumberRow updateInboundRouting(long companyId, long id, UpdateInboundRoutingRequest request);

    void releaseNumber(long companyId, long id);

    TestSipConnectionResponse testSipConnection(TestSipConnectionRequest request);
}
