package uz.murodjon.robotcallv2.crm.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmConnectorPort;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.application.port.input.CrmIntegrationUseCase;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    private final CrmIntegrationUseCase crmIntegrationUseCase;
    private final List<CrmConnectorPort> connectors;

    public CrmService(CrmProperties crmProperties, CrmIntegrationUseCase crmIntegrationUseCase, List<CrmConnectorPort> connectors) {
        this.crmProperties = crmProperties;
        this.crmIntegrationUseCase = crmIntegrationUseCase;
        this.connectors = connectors;
    }

    /**
     * Whether the platform's Uysot connection is configured. A company's own amoCRM, Kommo
     * or Bitrix24 connection does not depend on it.
     */
    private boolean isPlatformUysotConfigured() {
        return crmProperties.enabled() && crmProperties.baseUrl() != null && !crmProperties.baseUrl().isBlank();
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
     * copy of the note on the lead.
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
                leadId, callUuid, startedAt, durationSeconds, inbound, answered, clientPhone, recordingUrl));
    }

    /**
     * Where this company's CRM traffic goes, or {@code null} when there is nowhere to send
     * it. A connection that cannot be read — a stored secret that no longer decrypts —
     * counts as nowhere: the call goes ahead without the CRM.
     */
    private Route routeFor(long companyId) {
        CrmConnection connection;
        try {
            connection = connectionFor(companyId);
        } catch (RuntimeException e) {
            log.warn("CRM connection of company {} could not be read: {}", companyId, e.getMessage());
            return null;
        }
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

    private CrmConnection connectionFor(long companyId) {
        Optional<CrmConnection> own = crmIntegrationUseCase.findConnection(companyId);
        if (own.isPresent() && own.get().provider() != CrmProvider.UYSOT) {
            // Chosen but not (or no longer) connected: nothing to call. Falling back to
            // Uysot would write this company's calls into an account that is not theirs.
            return own.get().baseUrl() != null ? own.get() : null;
        }
        if (!isPlatformUysotConfigured()) {
            return null;
        }
        String token = own.map(CrmConnection::accessToken).orElse(null);
        if (token == null && crmProperties.apiToken() != null && !crmProperties.apiToken().isBlank()) {
            token = crmProperties.apiToken();
        }
        return new CrmConnection(CrmProvider.UYSOT, crmProperties.baseUrl(), token);
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
