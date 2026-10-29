package uz.murodjon.robotcallv2.agent.dialog;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.metrics.RealtimeMetrics;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogStarterTest {

    @Test
    void countsFallbackWhenRealtimeEngineIsUnavailable() {
        RealtimeDialogEngine realtimeDialogEngine = mock(RealtimeDialogEngine.class);
        when(realtimeDialogEngine.available()).thenReturn(false);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DialogStarter dialogStarter = new DialogStarter(realtimeDialogEngine, new RealtimeMetrics(meterRegistry));

        PipelineMode mode = dialogStarter.resolveMode("channel-1", null, PipelineMode.REALTIME);

        assertThat(mode).isEqualTo(PipelineMode.CASCADE);
        assertThat(meterRegistry.get("voice.realtime.fallbacks")
                .tag("engine", "unknown")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
