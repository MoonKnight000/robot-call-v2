package uz.murodjon.robotcallv2.phonenumber.application.port.input;

import uz.murodjon.robotcallv2.phonenumber.application.dto.AddPoolNumberRequest;
import uz.murodjon.robotcallv2.phonenumber.application.dto.PhoneNumberRow;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface PlatformPhoneNumberUseCase {

    PhoneNumberRow addPoolNumber(AddPoolNumberRequest request);

    int addBatchPoolNumbers(List<AddPoolNumberRequest> requests);

    PageableData<PhoneNumberRow> findAllPoolNumbers(PhoneNumberFilter filter);

    void removePoolNumber(long id);
}
