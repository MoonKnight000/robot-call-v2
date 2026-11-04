package uz.murodjon.robotcallv2.agent.vad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VadScorerFactoryTest {

    private final SileroVad silero = mock(SileroVad.class);
    private final TenVad ten = mock(TenVad.class);
    private final VadScorer sileroStream = named("silero");
    private final VadScorer tenStream = named("ten");

    private static VadScorer named(String name) {
        return new VadScorer() {
            @Override
            public float score(float[] window) {
                return 0f;
            }

            @Override
            public String engine() {
                return name;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private VadScorerFactory factory(VadEngine engine, boolean tenLoaded) {
        when(silero.available()).thenReturn(true);
        when(silero.openStream()).thenReturn(sileroStream);
        when(ten.available()).thenReturn(tenLoaded);
        when(ten.openStream()).thenReturn(tenStream);
        ObjectProvider<SileroVad> sileroProvider = mock(ObjectProvider.class);
        ObjectProvider<TenVad> tenProvider = mock(ObjectProvider.class);
        when(sileroProvider.getIfAvailable()).thenReturn(silero);
        when(tenProvider.getIfAvailable()).thenReturn(ten);
        VadProperties vadProperties = new VadProperties(true, "dummy.onnx", 8000, 256, 0.4f, 240, 250, null,
                0.4f, 100.0, 2.5);
        return new VadScorerFactory(sileroProvider, tenProvider, vadProperties, engine);
    }

    @Test
    void autoPrefersTenWhenItIsLoaded() {
        assertThat(factory(VadEngine.AUTO, true).open("c").engine()).isEqualTo("ten");
        assertThat(factory(VadEngine.AUTO, false).open("c").engine()).isEqualTo("silero");
    }

    @Test
    void sileroIsUsedEvenWhenTenIsLoaded() {
        assertThat(factory(VadEngine.SILERO, true).open("c").engine()).isEqualTo("silero");
    }

    @Test
    void tenFallsBackToSileroWhenNotLoaded() {
        assertThat(factory(VadEngine.TEN, false).open("c").engine()).isEqualTo("silero");
    }

    @Test
    void mixedScoresWithBothOrWithTheOneLoaded() {
        assertThat(factory(VadEngine.MIXED, true).open("c").engine()).isEqualTo("mixed-silero-ten");
        assertThat(factory(VadEngine.MIXED, false).open("c").engine()).isEqualTo("silero");
    }
}
