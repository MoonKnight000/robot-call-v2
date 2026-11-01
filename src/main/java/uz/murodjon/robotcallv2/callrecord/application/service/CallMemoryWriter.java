package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactUseCase;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a finished call's summary into what is kept about the client across calls: the
 * cross-call memory and the contact's custom fields. Shared by the live finalizer and the
 * outbox retry so both paths remember the same thing. Never throws: this is a nicety on top
 * of the call record, not a reason to fail it.
 */
@Component
public class CallMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(CallMemoryWriter.class);

    private final CallRecordService callRecordService;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final ContactUseCase contactUseCase;

    public CallMemoryWriter(CallRecordService callRecordService, ClientMemoryUseCase clientMemoryUseCase,
                            ContactUseCase contactUseCase) {
        this.callRecordService = callRecordService;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.contactUseCase = contactUseCase;
    }

    public void remember(long callAttemptId, long companyId, Scenario scenario, Disposition disposition,
                         CallSummary summary) {
        if (summary == null || summary.summary() == null || summary.summary().isBlank()) {
            return;
        }
        try {
            String phone = callRecordService.findPhoneByAttemptId(callAttemptId);
            if (phone == null) {
                return;
            }
            RememberedCall call = new RememberedCall(Instant.now(),
                    scenario != null ? scenario.scenarioKey() : null, disposition, summary.summary());
            clientMemoryUseCase.rememberCall(companyId, phone, call, summary.outcome());
        } catch (Exception e) {
            log.warn("[{}] could not update client memory: {}", callAttemptId, e.getMessage());
        }
    }

    /**
     * Copies the extracted values of {@code agent}'s fields that name a {@code contactField}
     * into the contact with {@code phone} (Retell's "Save to contact field"). A field the call
     * left empty is not written, so an earlier value stays.
     */
    public void saveContactFields(long callAttemptId, long companyId, String phone, AiAgent agent,
                                  CallSummary summary) {
        if (agent == null || summary == null || summary.outcome() == null || phone == null || phone.isBlank()) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (DataExtractionField field : agent.dataNeeded()) {
            Object value = summary.outcome().get(SummaryService.findOutcomeKey(field));
            if (field.contactField() != null && value != null) {
                values.put(field.contactField(), value.toString());
            }
        }
        if (values.isEmpty()) {
            return;
        }
        try {
            contactUseCase.saveCustomFieldsByPhone(companyId, phone, values);
        } catch (Exception e) {
            log.warn("[{}] could not save extracted fields to the contact: {}", callAttemptId, e.getMessage());
        }
    }
}
