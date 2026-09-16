package uz.murodjon.robotcallv2.callflow.application.port.input;

import uz.murodjon.robotcallv2.callflow.application.dto.CallFlowValidationResponse;
import uz.murodjon.robotcallv2.callflow.application.dto.SaveCallFlowRequest;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowFilter;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface CallFlowUseCase {

    CallFlow create(long companyId, SaveCallFlowRequest request);

    /** Saves the draft; what calls run changes only on {@link #publish}. */
    CallFlow update(long companyId, long id, SaveCallFlowRequest request);

    void delete(long companyId, long id);

    CallFlow requireFlow(long companyId, long id);

    /** The flow, which must have been published at least once — what an inbound route may point at. */
    CallFlow requirePublishedFlow(long companyId, long id);

    PageableData<CallFlow> list(long companyId, CallFlowFilter filter);

    CallFlowValidationResponse validateDraft(long companyId, long id);

    /** Validates the draft and makes it what calls run. */
    CallFlow publish(long companyId, long id);

    /** What a live call runs; null when the flow is gone or was never published. */
    CallFlowGraph findPublishedGraph(long companyId, long id);
}
