package uz.murodjon.robotcallv2.agent.ari;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.agent.dialog.PreCallWorkflowRunner;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.WorkflowFunction;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pre-call functions of a dialled call run while it rings and are handed to the setup that answers it. */
class RingingPreCallRunnerTest {

    private final PreCallWorkflowRunner preCallWorkflowRunner = mock(PreCallWorkflowRunner.class);
    private final CallAttemptUseCase callAttemptUseCase = mock(CallAttemptUseCase.class);
    private final DialogProperties dialogProperties = mock(DialogProperties.class);
    private final PendingCallRegistry registry = new PendingCallRegistry(mock(OutboundCallTrackingUseCase.class));
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final RingingPreCallRunner runner = new RingingPreCallRunner(preCallWorkflowRunner, callAttemptUseCase,
            dialogProperties, registry, executor);

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    private static OutboundCall call(List<WorkflowFunction> preCallFunctions) {
        AiAgent agent = mock(AiAgent.class);
        when(agent.preCallFunctions()).thenReturn(preCallFunctions);
        when(agent.defaultVariables()).thenReturn(Map.of("branch", "Chilonzor"));
        return new OutboundCall(1L, 2L, 3L, "+998901234567", "uz-UZ", null,
                new CallContext(Map.of("name", "Ali"), null), agent, 7L, null, null, null, null);
    }

    @Test
    void theLookupsStartedWhileRingingAreWhatTheAnsweringSetupGets() {
        OutboundCall call = call(List.of(mock(WorkflowFunction.class)));
        when(callAttemptUseCase.findAttemptIdByChannel("ch-1")).thenReturn(42L);
        when(preCallWorkflowRunner.run(eq(call.agent()), eq(7L), eq("ch-1"), eq(42L), eq("uz-UZ"),
                eq("+998901234567"), any())).thenReturn(Map.of("client_id", "C-9"));

        runner.start("ch-1", 7L, call);
        CompletableFuture<Map<String, Object>> run = registry.takePreCallRun("ch-1");

        assertThat(run).isNotNull();
        assertThat(RingingPreCallRunner.await(run)).containsEntry("client_id", "C-9");
        assertThat(registry.takePreCallRun("ch-1")).as("taken once").isNull();
    }

    @Test
    void anAgentWithoutPreCallFunctionsStartsNothing() {
        runner.start("ch-2", 7L, call(List.of()));

        assertThat(registry.takePreCallRun("ch-2")).isNull();
    }

    @Test
    void aCallNobodyAnsweredLeavesNoRunBehind() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(preCallWorkflowRunner.run(any(), anyLong(), anyString(), anyLong(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    release.await();
                    return Map.of();
                });
        runner.start("ch-3", 7L, call(List.of(mock(WorkflowFunction.class))));

        registry.forgetUnanswered("ch-3");
        release.countDown();

        assertThat(registry.takePreCallRun("ch-3")).isNull();
    }

    @Test
    void aFailedRunGivesNothingRatherThanBreakingTheCall() {
        CompletableFuture<Map<String, Object>> failed = CompletableFuture.failedFuture(new RuntimeException("crm down"));

        assertThat(RingingPreCallRunner.await(failed)).isEmpty();
    }
}
