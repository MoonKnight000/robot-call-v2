package uz.murodjon.robotcallv2.agent.ari;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.flow.FlowPrompter;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGateway;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGatewayResolver;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OperatorAcceptanceTest {

    private final CallSessionRegistry registry = mock(CallSessionRegistry.class);
    private final TelephonyGateway gateway = mock(TelephonyGateway.class);
    private final TelephonyGatewayResolver gatewayResolver = mock(TelephonyGatewayResolver.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void aCallerIsSettledOnce() {
        OperatorAcceptance acceptance = acceptance(false);
        acceptance.onOperatorDialled("caller", true);

        assertThat(acceptance.settle("caller")).isTrue();
        // Whichever of "connect" and "fall back" comes second finds the caller taken.
        assertThat(acceptance.settle("caller")).isFalse();
    }

    @Test
    void withoutRequireAcceptanceTheOperatorIsConnectedOnAnswer() {
        OperatorAcceptance acceptance = acceptance(false);
        acceptance.onOperatorDialled("caller", true);

        assertThat(acceptance.awaitAcceptance("caller", "op", "uz-UZ")).isTrue();
    }

    @Test
    void aSupervisorIsNeverAskedToAccept() {
        OperatorAcceptance acceptance = acceptance(true);
        acceptance.onOperatorDialled("caller", false);

        assertThat(acceptance.awaitAcceptance("caller", "op", "uz-UZ")).isTrue();
    }

    @Test
    void theOperatorAcceptsWithOneAndDeclinesWithAnythingElse() throws Exception {
        OperatorAcceptance acceptance = acceptance(true);

        acceptance.onOperatorDialled("caller-1", true);
        assertThat(pressWhileWaiting(acceptance, "caller-1", "op-1", '1')).isTrue();

        acceptance.onOperatorDialled("caller-2", true);
        assertThat(pressWhileWaiting(acceptance, "caller-2", "op-2", '2')).isFalse();
    }

    @Test
    void aFallBackForACallerWhoLeftHangsNothingUp() {
        OperatorAcceptance acceptance = acceptance(false);
        acceptance.onOperatorDialled("caller", true);
        when(registry.find("caller")).thenReturn(null);

        acceptance.fallBack("caller");
        acceptance.fallBack("caller");

        verify(gateway, never()).hangup(anyString());
        assertThat(acceptance.settle("caller")).isFalse();
    }

    private boolean pressWhileWaiting(OperatorAcceptance acceptance, String callerId, String operatorId, char digit)
            throws Exception {
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(
                () -> acceptance.awaitAcceptance(callerId, operatorId, "uz-UZ"), executor);
        long deadline = System.currentTimeMillis() + 2000;
        while (!acceptance.onDigit(operatorId, digit) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return result.get(3, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private OperatorAcceptance acceptance(boolean requireAcceptance) {
        ObjectProvider<FlowPrompter> noPrompter = mock(ObjectProvider.class);
        OperatorProperties properties = new OperatorProperties(true, null, 30, null, requireAcceptance, 5);
        when(gatewayResolver.findByCallId(anyString())).thenReturn(gateway);
        return new OperatorAcceptance(registry, gatewayResolver, mock(CallMediaFactory.class), noPrompter,
                properties, executor);
    }
}
