package uz.murodjon.robotcallv2.crm.application.port.input;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.crm.application.dto.CreateCrmTaskRequest;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateLeadStageRequest;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/**
 * The call pipeline's CRM: who a call is about, and where what it produced is written — in
 * whichever CRM the company uses. Nothing here throws: a CRM that is down answers
 * {@code null} / {@code false}, and the call goes ahead on what the platform already knows.
 */
public interface CrmUseCase {

    /** The lead a placed call is about, by its CRM id; {@code null} when unknown. */
    CrmClientSnapshot fetchClient(long companyId, Long leadId);

    /** Who is calling, for an inbound call that arrived with nothing but a number. */
    CrmClientSnapshot findByPhone(long companyId, String phone);

    /** Whether this company's CRM traffic has anywhere to go. */
    boolean isConnected(long companyId);

    /**
     * Writes a note onto the lead.
     *
     * @return what the CRM accepted the write under, or {@code null} when it did not
     */
    String postNote(long companyId, long leadId, String noteText);

    /**
     * Records the conversation on the lead's call history.
     *
     * @param callUuid this platform's own id for the conversation — the idempotency key
     * @return whether the CRM accepted the write
     */
    boolean postCallHistory(long companyId, long leadId, String callUuid, Instant startedAt,
                            int durationSeconds, boolean inbound, boolean answered,
                            String clientPhone, String recordingUrl);

    /**
     * Schedules a task or showroom meeting on the lead in the CRM.
     *
     * @return async request id or task id, or {@code null} if failed/unsupported
     */
    String createTask(long companyId, CreateCrmTaskRequest request);

    /**
     * Updates the lead's status/stage in the CRM pipeline.
     *
     * @return whether the status update was accepted
     */
    boolean updateLeadStage(long companyId, UpdateLeadStageRequest request);

    /**
     * Automatically updates the CRM pipeline stage of the lead based on the call
     * outcome (e.g. showroom booked, payment promise, refused, or callback needed).
     *
     * @return whether the stage sync was executed successfully
     */
    boolean syncLeadStage(long companyId, long leadId, CallSummary summary, Disposition disposition);

    /** The note as a person reads it in the CRM. */
    String composeNoteText(CallSummary summary);

    /** The note as a person reads it in the CRM, optionally including direct audio recording link. */
    String composeNoteText(CallSummary summary, Long recordingFileId);
}
