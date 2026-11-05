package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.agent.dialog.PreCallWorkflowRunner;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a dialled call's pre-call functions while its phone rings, so the lookups are done
 * (or nearly) by the time the client answers instead of adding to the silence before the
 * greeting. Everything they read is known at dial time: the campaign context, the agent's
 * default variables and the number.
 *
 * <p>The cost: a call nobody answers has still run its READ lookups.
 */
@Component
class RingingPreCallRunner {

    private static final Logger log = LoggerFactory.getLogger(RingingPreCallRunner.class);

    /** The runner bounds itself by its budget; the extra second only covers its own overhead. */
    private static final long AWAIT_MILLIS = PreCallWorkflowRunner.BUDGET_MILLIS + 1_000;

    private final PreCallWorkflowRunner preCallWorkflowRunner;
    private final CallAttemptUseCase callAttemptUseCase;
    private final DialogProperties dialogProperties;
    private final PendingCallRegistry pendingCallRegistry;
    private final ExecutorService callExecutor;

    RingingPreCallRunner(PreCallWorkflowRunner preCallWorkflowRunner, CallAttemptUseCase callAttemptUseCase,
                         DialogProperties dialogProperties, PendingCallRegistry pendingCallRegistry,
                         ExecutorService callExecutor) {
        this.preCallWorkflowRunner = preCallWorkflowRunner;
        this.callAttemptUseCase = callAttemptUseCase;
        this.dialogProperties = dialogProperties;
        this.pendingCallRegistry = pendingCallRegistry;
        this.callExecutor = callExecutor;
    }

    /**
     * Starts the pre-call functions of the call just dialled on {@code channelId}; the setup
     * that answers it takes the run from {@link PendingCallRegistry#takePreCallRun}. The facts
     * are built the way that setup builds them, so the functions see the same variables.
     */
    void start(String channelId, long companyId, OutboundCall outbound) {
        AiAgent agent = outbound.agent();
        if (agent == null || agent.preCallFunctions().isEmpty() || outbound.context() == null) {
            return;
        }
        CallContext context = outbound.context();
        if (PhoneNumbers.isValid(outbound.phone())) {
            context = context.withPhone(outbound.phone());
        }
        Map<String, Object> facts = context.withDefaultFacts(agent.defaultVariables()).facts();
        String language = outbound.language() != null ? outbound.language() : dialogProperties.language();
        CompletableFuture<Map<String, Object>> run = CompletableFuture.supplyAsync(() -> {
            AtomicReference<Map<String, Object>> produced = new AtomicReference<>(Map.of());
            CallMdc.with(channelId, () -> {
                long attemptId = callAttemptUseCase.findAttemptIdByChannel(channelId);
                produced.set(preCallWorkflowRunner.run(agent, companyId, channelId, attemptId, language,
                        outbound.phone(), facts));
                log.info("Pre-call functions of agent {} done while ringing ({} variable(s))",
                        agent.id(), produced.get().size());
            });
            return produced.get();
        }, callExecutor);
        pendingCallRegistry.registerPreCallRun(channelId, run);
    }

    /** What a run started at dial time produced; empty when it failed or overran its budget. */
    static Map<String, Object> await(CompletableFuture<Map<String, Object>> run) {
        try {
            return run.get(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (ExecutionException | TimeoutException | CancellationException e) {
            log.warn("Pre-call functions started while ringing gave nothing: {}", e.toString());
            return Map.of();
        }
    }
}
