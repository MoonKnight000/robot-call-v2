package uz.murodjon.robotcallv2.crm.application.port.output;

import uz.murodjon.robotcallv2.crm.application.dto.CreateCrmTaskRequest;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateLeadStageRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CreateCrmLeadRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

/**
 * One CRM product, as the call pipeline uses it: who a call is about, and where its
 * outcome is written. CrmService picks the connector for which {@link #isSupported} holds for the company's
 * provider.
 *
 * <p>Nothing here throws. A CRM that is down, slow or refusing answers {@code null} /
 * {@code false}: the call it is being asked about goes ahead on what the platform already
 * knows, and a note that did not land is re-posted by the outbox.
 */
public interface CrmConnectorPort {

    boolean isSupported(CrmProvider provider);

    /** The record a placed call is about, by the CRM's own id; {@code null} when unknown. */
    CrmClientSnapshot findLead(CrmConnection connection, long leadId);

    /** Who is calling, for an inbound call that arrived with nothing but a number. */
    CrmClientSnapshot findByPhone(CrmConnection connection, String phone);

    /**
     * @return what the CRM took the note under — its id, or an async request id — or
     *         {@code null} when it did not take it
     */
    String postNote(CrmConnection connection, long leadId, String noteText);

    /** @return whether the CRM accepted the call-log entry */
    boolean postCallHistory(CrmConnection connection, CrmCallHistoryEntry entry);

    /**
     * Creates a scheduled task/meeting in the CRM for a qualified lead.
     *
     * @return async request id or task id, or {@code null} when skipped/refused
     */
    String createTask(CrmConnection connection, CreateCrmTaskRequest request);

    /**
     * Updates the lead's status/stage in the CRM pipeline.
     *
     * @return whether the status update was accepted
     */
    boolean updateLeadStage(CrmConnection connection, UpdateLeadStageRequest request);

    /**
     * Opens a lead. Only Uysot does this for now; the others answer {@code null}.
     *
     * @return what the CRM accepted the write under, or {@code null} when it did not
     */
    default String createLead(CrmConnection connection, CreateCrmLeadRequest request) {
        return null;
    }
}
