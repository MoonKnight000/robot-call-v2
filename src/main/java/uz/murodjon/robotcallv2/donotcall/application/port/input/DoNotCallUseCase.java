package uz.murodjon.robotcallv2.donotcall.application.port.input;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/**
 * Inbound UseCase port for Do-Not-Call (DNC) list operations (§10.8).
 */
public interface DoNotCallUseCase {

    PageableData<DoNotCallRow> list(DoNotCallFilter filter);

    DoNotCallRemoveResponse remove(String phone);

    void add(long companyId, String phone, String reason, DoNotCallSource source);

    boolean contains(long companyId, String phone);
}
