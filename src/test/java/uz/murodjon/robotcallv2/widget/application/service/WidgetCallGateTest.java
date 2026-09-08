package uz.murodjon.robotcallv2.widget.application.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentLimits;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WidgetCallGateTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final Instant NOON = Instant.parse("2026-09-07T07:00:00Z");

    @Test
    void anAgentWithNoLimitsStillMeetsTheDefaultCeiling() {
        WidgetCallGate gate = new WidgetCallGate(Clock.fixed(NOON, TASHKENT));
        AiAgent agent = agentWith(null, null);

        // Unset is not unlimited on a public endpoint: the fifth concurrent session is
        // the last one the default ceiling lets through.
        for (int i = 0; i < 5; i++) {
            gate.checkCallAllowed(agent);
            gate.recordBooking(agent, "session-" + i);
        }

        assertThatThrownBy(() -> gate.checkCallAllowed(agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("5");
    }

    @Test
    void theAgentsOwnLimitWinsOverTheDefault() {
        WidgetCallGate gate = new WidgetCallGate(Clock.fixed(NOON, TASHKENT));
        AiAgent agent = agentWith(8, null);

        for (int i = 0; i < 8; i++) {
            gate.checkCallAllowed(agent);
            gate.recordBooking(agent, "session-" + i);
        }

        assertThatThrownBy(() -> gate.checkCallAllowed(agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("8");
    }

    @Test
    void theConcurrentLimitRefusesTheCallAfterIt() {
        WidgetCallGate gate = new WidgetCallGate(Clock.fixed(NOON, TASHKENT));
        AiAgent agent = agentWith(2, null);

        gate.checkCallAllowed(agent);
        gate.recordBooking(agent, "a");
        gate.checkCallAllowed(agent);
        gate.recordBooking(agent, "b");

        assertThatThrownBy(() -> gate.checkCallAllowed(agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2");
    }

    @Test
    void anAbandonedSessionStopsHoldingItsLineAfterTheTimeout() {
        MutableClock clock = new MutableClock(NOON);
        WidgetCallGate gate = new WidgetCallGate(clock);
        AiAgent agent = agentWith(1, null);

        gate.checkCallAllowed(agent);
        gate.recordBooking(agent, "never-dialled-in");
        assertThatThrownBy(() -> gate.checkCallAllowed(agent)).isInstanceOf(ConflictException.class);

        // Past the five minutes Asterisk keeps the pending session for, the slot is free:
        // that browser can no longer dial in with it.
        clock.advance(Duration.ofMinutes(6));
        assertThatCode(() -> gate.checkCallAllowed(agent)).doesNotThrowAnyException();
    }

    @Test
    void theDailyLimitRefusesTheCallAfterIt() {
        WidgetCallGate gate = new WidgetCallGate(Clock.fixed(NOON, TASHKENT));
        AiAgent agent = agentWith(null, 3);

        for (int i = 0; i < 3; i++) {
            gate.checkCallAllowed(agent);
            gate.recordBooking(agent, "session-" + i);
        }

        assertThatThrownBy(() -> gate.checkCallAllowed(agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3");
    }

    @Test
    void theDailyCountResetsOnTheAgentsOwnCalendarDay() {
        MutableClock clock = new MutableClock(NOON);
        WidgetCallGate gate = new WidgetCallGate(clock);
        AiAgent agent = agentWith(null, 1);

        gate.checkCallAllowed(agent);
        gate.recordBooking(agent, "yesterday");
        assertThatThrownBy(() -> gate.checkCallAllowed(agent)).isInstanceOf(ConflictException.class);

        clock.advance(Duration.ofDays(1));
        assertThatCode(() -> gate.checkCallAllowed(agent)).doesNotThrowAnyException();
    }

    @Test
    void aBookingThatNeverHappenedDoesNotSpendTheAllowance() {
        WidgetCallGate gate = new WidgetCallGate(Clock.fixed(NOON, TASHKENT));
        AiAgent agent = agentWith(null, 1);

        // The check passed but the session was never booked — a scenario that failed to
        // resolve, say. The allowance must be untouched.
        gate.checkCallAllowed(agent);
        gate.checkCallAllowed(agent);

        assertThatCode(() -> {
            gate.checkCallAllowed(agent);
            gate.recordBooking(agent, "the-one-that-worked");
        }).doesNotThrowAnyException();
    }

    private static AiAgent agentWith(Integer concurrent, Integer daily) {
        return AiAgentFixtures.agent(7L, 1L, 3L, "uz-UZ", "nigora")
                .withLimits(new AiAgentLimits(null, null, null, concurrent, daily));
    }

    /** A clock the test moves by hand — the gate's whole job is about elapsed time. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return TASHKENT;
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
}
