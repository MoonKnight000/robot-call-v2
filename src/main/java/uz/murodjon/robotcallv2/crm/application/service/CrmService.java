package uz.murodjon.robotcallv2.crm.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.crm.application.dto.CreateCrmTaskRequest;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateLeadStageRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmSettingsUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmConnectorPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

/**
 * The call pipeline's CRM: reads the lead a call is about and writes back what the call
 * produced (PROJECT.md Stage 9, MASTER_ROADMAP.md §11), in whichever CRM the company uses.
 *
 * <p>A company that connected amoCRM, Kommo or Bitrix24 in its integration settings is
 * served by that product's connector. Every other company goes to Uysot — with its own
 * OAuth token when it connected one, the platform's static token otherwise — exactly as
 * before there was a choice. Each product's wire format lives in its {@link CrmConnectorPort};
 * what is decided here is where a request goes and what a note says.
 */
@Component
public class CrmService implements CrmUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrmService.class);

    /**
     * How much of the composed note is sent. The endpoint documents no limit, and a note
     * is read by a person: everything past a couple of screens is the summary having gone
     * wrong, not detail worth storing.
     */
    private static final int MAX_NOTE_CHARS = 4000;

    private final CrmProperties crmProperties;
    private final List<CrmConnectorPort> connectors;
    private final CrmSettingsUseCase crmSettingsUseCase;
    private final CrmConnectionResolver connectionResolver;

    public CrmService(CrmProperties crmProperties, CrmIntegrationUseCase crmIntegrationUseCase, List<CrmConnectorPort> connectors,
                      CompanyUseCase companyUseCase, CrmSettingsUseCase crmSettingsUseCase) {
        this.crmProperties = crmProperties;
        this.connectors = connectors;
        this.crmSettingsUseCase = crmSettingsUseCase;
        this.connectionResolver = new CrmConnectionResolver(crmProperties, crmIntegrationUseCase, companyUseCase);
    }

    @Override
    public boolean syncLeadStage(long companyId, long leadId, CallSummary summary, Disposition disposition) {
        UpdateLeadStageRequest request = resolveLeadStageUpdate(companyId, leadId, summary, disposition);
        return request != null && updateLeadStage(companyId, request);
    }

    @Override
    public UpdateLeadStageRequest resolveLeadStageUpdate(long companyId, long leadId, CallSummary summary,
                                                          Disposition disposition) {
        if (leadId <= 0 || !isConnected(companyId)) {
            return null;
        }
        Integer targetStatusId = null;
        String reason = null;
        CrmSettings settings = crmSettingsUseCase.findByCompanyId(companyId);

        if (summary != null && summary.outcome() != null) {
            Object explicitStatus = summary.outcome().get("targetStatusId");
            if (explicitStatus instanceof Number n) {
                targetStatusId = n.intValue();
                reason = "AI bot outcome stage update";
            }
        }

        if (targetStatusId == null && summary != null && Boolean.TRUE.equals(summary.outcome().get("meetingBooked"))) {
            targetStatusId = firstSet(settings.meetingBookedStatusId(), crmProperties.meetingBookedStatusId());
            reason = "Sotuv ofisiga uchrashuv belgilandi";
        } else if (targetStatusId == null && (disposition == Disposition.PROMISE_TO_PAY || (summary != null && summary.outcome().containsKey("promisedDate")))) {
            targetStatusId = firstSet(settings.promiseToPayStatusId(), crmProperties.promiseToPayStatusId());
            reason = "To'lov va'dasi olindi";
        } else if (targetStatusId == null && (disposition == Disposition.REFUSED || disposition == Disposition.DO_NOT_CALL)) {
            targetStatusId = firstSet(settings.notInterestedStatusId(), crmProperties.notInterestedStatusId());
            reason = "Mijoz rad etdi (" + disposition + ")";
        } else if (targetStatusId == null && (disposition == Disposition.NO_ANSWER || disposition == Disposition.CALLBACK_REQUESTED || disposition == Disposition.CARRIER_REJECTED)) {
            targetStatusId = firstSet(settings.callbackStatusId(), crmProperties.callbackStatusId());
            reason = "Bog'lanib bo'lmadi (" + disposition + ")";
        }

        if (targetStatusId != null && targetStatusId > 0) {
            return new UpdateLeadStageRequest(leadId, targetStatusId, reason);
        }
        return null;
    }

    /** The company's own value when it saved one, the platform's otherwise. */
    private static Integer firstSet(Integer own, Integer platform) {
        return own != null ? own : platform;
    }

    @Override
    public String composeNoteText(CallSummary summary, Long recordingFileId) {
        String base = composeNoteText(summary);
        if (recordingFileId != null && recordingFileId > 0) {
            return base + "\nAudio yozuv: /api/files/" + recordingFileId;
        }
        return base;
    }

    /**
     * The lead a placed call is about, by its CRM id — {@code campaign_target.client_id}
     * carries that id for every target the debtor import queued.
     */
    @Override
    public CrmClientSnapshot fetchClient(long companyId, Long leadId) {
        if (leadId == null || leadId == 0) {
            return null;
        }
        Route route = routeFor(companyId);
        return route != null ? route.connector().findLead(route.connection(), leadId) : null;
    }

    /** Who is calling, for an inbound call that arrived with nothing but a number. */
    @Override
    public CrmClientSnapshot findByPhone(long companyId, String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        Route route = routeFor(companyId);
        return route != null ? route.connector().findByPhone(route.connection(), phone) : null;
    }

    /**
     * Whether this company's CRM traffic has anywhere to go — its own connection, or the
     * platform's Uysot. The post-call outbox queues nothing for a company where it has not.
     */
    @Override
    public boolean isConnected(long companyId) {
        return routeFor(companyId) != null;
    }

    /**
     * Writes a note onto the lead.
     *
     * <p>Uysot's note endpoint takes no idempotency key: a write it accepted but whose answer
     * was lost comes back as {@code null}, and the outbox posting it again leaves a second
     * copy of the note on the lead.</p>
     *
     * @param noteText the note as {@link #composeNoteText} wrote it
     * @return what the CRM accepted the write under — Uysot's async request id, or the id
     *         amoCRM / Bitrix24 gave the note — or {@code null} when it did not: the outbox
     *         re-posts on {@code null} and stops on anything else.
     */
    @Override
    public String postNote(long companyId, long leadId, String noteText) {
        if (noteText == null || leadId == 0) {
            return null;
        }
        Route route = routeFor(companyId);
        if (route == null) {
            return null;
        }
        try {
            String reference = route.connector().postNote(route.connection(), leadId, noteText);
            if (reference != null) {
                log.info("CRM note enqueued for lead {} (requestId={})", leadId, reference);
            }
            return reference;
        } catch (Exception e) {
            log.warn("CRM note post failed for lead {}: {}", leadId, e.getMessage());
            return null;
        }
    }

    /**
     * Records the conversation itself on the lead's call history — separate from the note
     * because the CRMs model the two separately.
     *
     * @param callUuid this platform's own id for the conversation — the idempotency key,
     *                 so a re-post after a failed write does not create a second record
     * @return whether the CRM accepted the write
     */
    @Override
    public boolean postCallHistory(long companyId, long leadId, String callUuid, Instant startedAt,
                                   int durationSeconds, boolean inbound, boolean answered,
                                   String clientPhone, String recordingUrl) {
        if (leadId == 0 || callUuid == null || startedAt == null) {
            return false;
        }
        Route route = routeFor(companyId);
        return route != null && route.connector().postCallHistory(route.connection(), new CrmCallHistoryEntry(
                leadId, callUuid, startedAt, durationSeconds, inbound, answered, clientPhone, recordingUrl,
                crmSettingsUseCase.findByCompanyId(companyId).aiEmployeeId()));
    }

    @Override
    public String createTask(long companyId, CreateCrmTaskRequest request) {
        if (request == null || request.leadId() == 0) {
            return null;
        }
        Route route = routeFor(companyId);
        if (route == null) {
            return null;
        }
        if (request.responsibleId() == null) {
            Integer aiEmployeeId = crmSettingsUseCase.findByCompanyId(companyId).aiEmployeeId();
            if (aiEmployeeId != null) {
                request = new CreateCrmTaskRequest(request.leadId(), request.title(), request.description(),
                        request.dueAt(), aiEmployeeId.longValue());
            }
        }
        return route.connector().createTask(route.connection(), request);
    }

    @Override
    public boolean updateLeadStage(long companyId, UpdateLeadStageRequest request) {
        if (request == null || request.leadId() == 0) {
            return false;
        }
        Route route = routeFor(companyId);
        if (route == null) {
            return false;
        }
        return route.connector().updateLeadStage(route.connection(), request);
    }

    /** Where this company's CRM traffic goes, or {@code null} when there is nowhere to send it. */
    private Route routeFor(long companyId) {
        CrmConnection connection = connectionResolver.findConnection(companyId);
        if (connection == null) {
            return null;
        }
        for (CrmConnectorPort connector : connectors) {
            if (connector.isSupported(connection.provider())) {
                return new Route(connector, connection);
            }
        }
        log.warn("No CRM connector for provider {}", connection.provider());
        return null;
    }

    /**
     * The note as a person reads it in the CRM. One free-text field is all the endpoints
     * take, so the structured half of the summary — the promise, the sentiment, the
     * callback — is spelled out in the text rather than dropped.
     */
    @Override
    public String composeNoteText(CallSummary summary) {
        StringBuilder text = new StringBuilder();
        if (summary.summary() != null && !summary.summary().isBlank()) {
            text.append(summary.summary().trim());
        }
        appendOutcome(text, "Sabab", summary.outcome().get("reasonCode"));
        appendOutcome(text, "Va'da qilingan summa", summary.outcome().get("promisedAmount"));
        appendOutcome(text, "Va'da qilingan sana", summary.outcome().get("promisedDate"));
        appendOutcome(text, "Kerakli xonalar", summary.outcome().get("roomsNeeded"));
        appendOutcome(text, "To'lov shakli", summary.outcome().get("paymentMethod"));
        appendOutcome(text, "Byudjet", summary.outcome().get("budgetRange"));
        appendOutcome(text, "Uchrashuv vaqti", summary.outcome().get("meetingTime"));
        if (Boolean.TRUE.equals(summary.outcome().get("meetingBooked"))) {
            append(text, "Uchrashuv", "Sotuv ofisiga belgilandi");
        }
        appendOutcome(text, "Shartnoma raqami", summary.outcome().get("contractNumber"));
        if (Boolean.TRUE.equals(summary.outcome().get("managerCallbackRequested"))) {
            append(text, "Menejer qayta qo'ng'iroq qilsin", String.valueOf(
                    summary.outcome().getOrDefault("managerCallbackReason", "ha")));
        }
        if (summary.sentiment() != null) {
            append(text, "Kayfiyat", summary.sentiment().name());
        }
        if (summary.callbackAt() != null && !summary.callbackAt().isBlank()) {
            append(text, "Qayta qo'ng'iroq", summary.callbackAt());
        }
        if (summary.needsFollowUp()) {
            append(text, "Kuzatuv kerak", summary.followUpNote() != null ? summary.followUpNote() : "ha");
        }
        if (summary.qaScore() != null) {
            append(text, "Suhbat sifati", summary.qaScore() + "/100");
        }
        String note = text.toString().trim();
        if (note.isEmpty()) {
            note = "AI operator bilan suhbat bo'lib o'tdi.";
        }
        return note.length() > MAX_NOTE_CHARS ? note.substring(0, MAX_NOTE_CHARS) : note;
    }

    private static void appendOutcome(StringBuilder text, String label, Object value) {
        if (value != null && !value.toString().isBlank()) {
            append(text, label, value.toString());
        }
    }

    private static void append(StringBuilder text, String label, String value) {
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(label).append(": ").append(value);
    }

    /** A company's CRM traffic: the connector that speaks its product, and how to reach it. */
    private record Route(CrmConnectorPort connector, CrmConnection connection) {
    }
}
