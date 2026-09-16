package uz.murodjon.robotcallv2.operator.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.operator.application.port.output.OperatorStatusLogRepository;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OperatorPresenceServiceTest {

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-14T06:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private OperatorPresenceService presence;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        presence = new OperatorPresenceService(mock(OperatorStatusLogRepository.class), new ContactCenterEventHub(), clock);
        presence.setStatus(1, 10, OperatorStatus.AVAILABLE, null);
    }

    @Test
    void anUnknownOperatorIsOffline() {
        assertThat(presence.find(1, 99).status()).isEqualTo(OperatorStatus.OFFLINE);
    }

    @Test
    void onlyOneOfferCanClaimAnOperator() {
        assertThat(presence.claimForRinging(10, "call-a")).isTrue();
        assertThat(presence.claimForRinging(10, "call-b")).isFalse();
        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.RINGING);
    }

    @Test
    void aWithdrawnOfferIsNotAMiss() {
        presence.claimForRinging(10, "call-a");
        presence.releaseRinging(10);

        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.AVAILABLE);
        assertThat(presence.find(1, 10).consecutiveMisses()).isZero();
    }

    @Test
    void twoMissesInARowTakeTheOperatorOffTheQueue() {
        presence.claimForRinging(10, "call-a");
        presence.markMissed(10);
        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.AVAILABLE);

        presence.claimForRinging(10, "call-b");
        presence.markMissed(10);

        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.BREAK);
        assertThat(presence.find(1, 10).reason()).isEqualTo("javob bermadi");
    }

    @Test
    void anAnsweredCallResetsTheMissesAndCounts() {
        presence.claimForRinging(10, "call-a");
        presence.markMissed(10);
        presence.claimForRinging(10, "call-b");
        presence.markOnCall(10, "call-b");

        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.ON_CALL);
        assertThat(presence.find(1, 10).consecutiveMisses()).isZero();
        assertThat(presence.find(1, 10).callsToday()).isEqualTo(1);
    }

    @Test
    void wrapUpEndsByItself() {
        presence.claimForRinging(10, "call-a");
        presence.markOnCall(10, "call-a");
        presence.markCallEnded(10, 15);
        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.WRAP_UP);

        clock.advanceSeconds(10);
        presence.releaseWrapUps();
        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.WRAP_UP);

        clock.advanceSeconds(10);
        presence.releaseWrapUps();
        assertThat(presence.find(1, 10).status()).isEqualTo(OperatorStatus.AVAILABLE);
        assertThat(presence.find(1, 10).lastCallEndedAt()).isNotNull();
    }

    @Test
    void anOperatorOnBreakIsNotOffered() {
        presence.setStatus(1, 10, OperatorStatus.BREAK, "tushlik");

        assertThat(presence.claimForRinging(10, "call-a")).isFalse();
        assertThat(presence.find(1, 10).reason()).isEqualTo("tushlik");
    }
}
