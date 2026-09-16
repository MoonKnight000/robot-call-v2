package uz.murodjon.robotcallv2.agent.flow;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DtmfCollectorTest {

    private static boolean never() {
        return false;
    }

    @Test
    void collectsUpToTheLimit() throws InterruptedException {
        DtmfCollector digits = new DtmfCollector();
        "1025".chars().forEach(c -> digits.offer((char) c));

        assertThat(digits.collect(3, 100, 100, DtmfCollectorTest::never)).isEqualTo("102");
    }

    @Test
    void hashEndsTheEntryAndIsNotPartOfIt() throws InterruptedException {
        DtmfCollector digits = new DtmfCollector();
        "12#9".chars().forEach(c -> digits.offer((char) c));

        assertThat(digits.collect(6, 100, 100, DtmfCollectorTest::never)).isEqualTo("12");
    }

    @Test
    void nothingPressedIsEmpty() throws InterruptedException {
        assertThat(new DtmfCollector().collect(4, 50, 50, DtmfCollectorTest::never)).isEmpty();
    }

    @Test
    void aPauseEndsAMultiDigitEntry() throws InterruptedException {
        DtmfCollector digits = new DtmfCollector();
        digits.offer('7');

        assertThat(digits.collect(4, 100, 50, DtmfCollectorTest::never)).isEqualTo("7");
    }

    @Test
    void aHangUpEndsTheWaitAtOnce() throws InterruptedException {
        long started = System.currentTimeMillis();

        assertThat(new DtmfCollector().poll(5_000, () -> true)).isNull();
        assertThat(System.currentTimeMillis() - started).isLessThan(1_000);
    }

    @Test
    void aKeyFiresTheBargeInHook() {
        DtmfCollector digits = new DtmfCollector();
        AtomicInteger stops = new AtomicInteger();
        digits.onDigit(stops::incrementAndGet);

        digits.offer('1');

        assertThat(stops.get()).isEqualTo(1);
        assertThat(digits.hasPending()).isTrue();
    }
}
