package uz.murodjon.robotcallv2.callflow.application.port.output;

import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowFilter;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;

import java.time.Instant;
import java.util.List;

public interface CallFlowRepository {

    long create(long companyId, String name, String description, CallFlowGraph draft);

    void updateDraft(long companyId, long id, String name, String description, CallFlowGraph draft);

    /** Copies the draft into the published graph and bumps the version. */
    void publish(long companyId, long id, CallFlowGraph graph, Instant at);

    void delete(long companyId, long id);

    CallFlow find(long companyId, long id);

    List<CallFlow> findAll(long companyId, CallFlowFilter filter);

    long count(long companyId);
}
