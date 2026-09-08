package uz.murodjon.robotcallv2.donotcall.application.port.input;

import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRemoveResponse;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallRow;
import uz.murodjon.robotcallv2.donotcall.domain.entity.DoNotCallFilter;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/**
 * Inbound UseCase port for Do-Not-Call (DNC) list operations (§10.8).
 */
public interface DoNotCallUseCase {

    PageableData<DoNotCallRow> list(long companyId, DoNotCallFilter filter);

    DoNotCallRemoveResponse remove(long companyId, String phone);

    void add(long companyId, String phone, String reason, DoNotCallSource source);

    boolean contains(long companyId, String phone);
}
