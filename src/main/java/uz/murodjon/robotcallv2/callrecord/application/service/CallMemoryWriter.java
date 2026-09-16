package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;

/**
 * Turns a finished call's summary into the client's cross-call memory. Shared by the
 * live finalizer and the outbox retry so both paths remember the same thing. Never
 * throws: memory is a nicety on top of the call record, not a reason to fail it.
 */
@Component
public class CallMemoryWriter {

    private static final Logger log = LoggerFactory.getLogger(CallMemoryWriter.class);

    private final CallRecordService callRecordService;
    private final ClientMemoryUseCase clientMemoryUseCase;

    public CallMemoryWriter(CallRecordService callRecordService, ClientMemoryUseCase clientMemoryUseCase) {
        this.callRecordService = callRecordService;
        this.clientMemoryUseCase = clientMemoryUseCase;
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
}
