package uz.murodjon.robotcallv2.agent.vad;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MixedVadScorerTest {

    private static final float[] WINDOW = new float[256];

    /** A model that answers a fixed score and names its own thresholds. */
    private static final class FixedScorer implements VadScorer {
        private final String name;
        private final float bargeIn;
        private final float listening;
        float next;
        boolean closed;

        FixedScorer(String name, float bargeIn, float listening) {
            this.name = name;
            this.bargeIn = bargeIn;
            this.listening = listening;
        }

        @Override
        public float score(float[] window) {
            return next;
        }

        @Override
        public float bargeInThreshold() {
            return bargeIn;
        }

        @Override
        public float listeningThreshold() {
            return listening;
        }

        @Override
        public String engine() {
            return name;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private final FixedScorer silero = new FixedScorer("silero", 0.4f, 0.4f);
    private final FixedScorer ten = new FixedScorer("ten", 0.6f, 0.5f);
    private final MixedVadScorer mixed = new MixedVadScorer(List.of(silero, ten), 0.5f, 0.4f);

    @Test
    void eachModelsThresholdLandsOnTheCommonOne() {
        assertThat(MixedVadScorer.toCommonScale(0.4f, 0.4f)).isCloseTo(0.5f, within(1e-6f));
        assertThat(MixedVadScorer.toCommonScale(0.6f, 0.6f)).isCloseTo(0.5f, within(1e-6f));
        assertThat(MixedVadScorer.toCommonScale(1f, 0.6f)).isCloseTo(1f, within(1e-6f));
        assertThat(MixedVadScorer.toCommonScale(0f, 0.6f)).isZero();
    }

    @Test
    void speechStartsOnlyWhenBothModelsHearIt() {
        silero.next = 0.9f; // a cough Silero calls speech
        ten.next = 0.2f;    // and TEN does not

        assertThat(mixed.score(WINDOW)).isLessThan(mixed.bargeInThreshold());
    }

    @Test
    void speechContinuesWhileEitherModelHearsIt() {
        silero.next = 0.9f;
        ten.next = 0.9f;
        assertThat(mixed.score(WINDOW)).isGreaterThanOrEqualTo(mixed.bargeInThreshold());

        ten.next = 0.1f; // a soft word only Silero still hears
        assertThat(mixed.score(WINDOW)).isGreaterThanOrEqualTo(mixed.bargeInThreshold());

        silero.next = 0.05f; // now nobody does
        assertThat(mixed.score(WINDOW)).isLessThan(mixed.listeningThreshold());
    }

    @Test
    void aFailedModelIsLeftOutAndBothFailingIsAFailure() {
        silero.next = -1f;
        ten.next = 0.9f;
        assertThat(mixed.score(WINDOW)).isGreaterThanOrEqualTo(mixed.bargeInThreshold());

        ten.next = -1f;
        assertThat(mixed.score(WINDOW)).isEqualTo(-1f);
    }

    @Test
    void namesAndClosesEveryModel() {
        assertThat(mixed.engine()).isEqualTo("mixed-silero-ten");

        mixed.close();

        assertThat(silero.closed).isTrue();
        assertThat(ten.closed).isTrue();
    }
}
