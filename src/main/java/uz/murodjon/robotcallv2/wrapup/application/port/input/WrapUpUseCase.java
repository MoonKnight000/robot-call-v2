package uz.murodjon.robotcallv2.wrapup.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.wrapup.application.dto.SaveWrapUpCodeRequest;
import uz.murodjon.robotcallv2.wrapup.application.dto.WrapUpCallRequest;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCode;
import uz.murodjon.robotcallv2.wrapup.domain.entity.WrapUpCodeFilter;

/** The company's wrap-up codes, and operators closing their calls with one. */
public interface WrapUpUseCase {

    WrapUpCode createCode(long companyId, SaveWrapUpCodeRequest request);

    WrapUpCode updateCode(long companyId, long id, SaveWrapUpCodeRequest request);

    void deleteCode(long companyId, long id);

    WrapUpCode requireCode(long companyId, long id);

    PageableData<WrapUpCode> listCodes(long companyId, WrapUpCodeFilter filter);

    /**
     * Closes the current user's call with a code. Sent again, it replaces the earlier
     * wrap-up — an operator correcting a mis-click.
     */
    CallWrapUp recordCallWrapUp(long companyId, long callId, WrapUpCallRequest request);
}
