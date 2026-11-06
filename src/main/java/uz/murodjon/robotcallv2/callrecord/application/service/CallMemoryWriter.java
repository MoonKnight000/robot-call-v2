package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactFieldUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.ExtractedCallValues;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final ContactFieldUseCase contactFieldUseCase;

    public CallMemoryWriter(CallRecordService callRecordService, ClientMemoryUseCase clientMemoryUseCase,
                            ContactFieldUseCase contactFieldUseCase) {
        this.callRecordService = callRecordService;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.contactFieldUseCase = contactFieldUseCase;
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
            RememberedCall call = RememberedCall.ofCall(callAttemptId, Instant.now(),
                    scenario != null ? scenario.scenarioKey() : null, disposition, summary.summary());
            clientMemoryUseCase.rememberCall(companyId, phone, call, summary.outcome());
        } catch (Exception e) {
            log.warn("[{}] could not update client memory: {}", callAttemptId, e.getMessage());
        }
    }

    /**
     * Hands every value the call's extraction found to the contact with {@code phone}: the
     * company's contact fields pick theirs by {@code extractionKey} and apply their update mode,
     * and {@code agent}'s fields that name a {@code contactField} are written there too
     * (Retell's "Save to contact field"). A field the call left empty is not written, so an
     * earlier value stays.
     */
    public void saveContactFields(long callAttemptId, long companyId, String phone, AiAgent agent,
                                  CallSummary summary) {
        if (summary != null) {
            writeContactFields(callAttemptId, companyId, phone, agent, summary.outcome());
        }
    }

    /**
     * Contact backfill: a past call's stored {@code outcome}, written the same way, to the
     * contact with the call's own number.
     *
     * @return whether the contact was handed any value
     */
    public boolean saveStoredContactFields(long callAttemptId, long companyId, AiAgent agent,
                                           Map<String, Object> outcome) {
        return writeContactFields(callAttemptId, companyId, callRecordService.findPhoneByAttemptId(callAttemptId),
                agent, outcome);
    }

    private boolean writeContactFields(long callAttemptId, long companyId, String phone, AiAgent agent,
                                      Map<String, Object> outcome) {
        if (outcome == null || phone == null || phone.isBlank()) {
            return false;
        }
        Map<String, String> byExtractionKey = new LinkedHashMap<>();
        outcome.forEach((key, value) -> {
            String text = toText(value);
            if (key != null && text != null) {
                byExtractionKey.put(key, text);
            }
        });
        Map<String, String> byContactField = new LinkedHashMap<>();
        if (agent != null) {
            for (DataExtractionField field : agent.dataNeeded()) {
                String text = byExtractionKey.get(SummaryService.findOutcomeKey(field));
                if (field.contactField() != null && text != null) {
                    byContactField.put(field.contactField(), text);
                }
            }
        }
        ExtractedCallValues values = new ExtractedCallValues(callAttemptId,
                agent != null ? agent.fastLlmModel() : null, byExtractionKey, byContactField);
        if (values.isEmpty()) {
            return false;
        }
        try {
            contactFieldUseCase.applyCallValues(companyId, phone, values);
            return true;
        } catch (Exception e) {
            log.warn("[{}] could not save extracted fields to the contact: {}", callAttemptId, e.getMessage());
            return false;
        }
    }

    /** An extracted value as contact field text: a list joined by commas, nothing for empty or nested values. */
    private static String toText(Object value) {
        if (value == null || value instanceof Map<?, ?>) {
            return null;
        }
        String text = value instanceof Collection<?> items
                ? items.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining(", "))
                : value.toString();
        return text.isBlank() ? null : text.strip();
    }
}
