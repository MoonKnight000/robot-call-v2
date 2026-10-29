package uz.murodjon.robotcallv2.widget.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentLimits;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.widget.application.port.output.WidgetCallCounterPort;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetCallBooking;
import uz.murodjon.robotcallv2.widget.domain.enums.WidgetBookingResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The gate decides which limits a widget call is booked against and what a refusal is;
 * the counting itself is one atomic step in the store behind {@link WidgetCallCounterPort}.
 */
class WidgetCallGateTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final Instant NOON = Instant.parse("2026-09-07T07:00:00Z");

    private final WidgetCallCounterPort counter = mock(WidgetCallCounterPort.class);

    @Test
    void anAgentWithNoLimitsIsBookedAgainstTheDefaultCeiling() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.BOOKED);

        // Unset is not unlimited on a public endpoint.
        WidgetCallBooking booking = gateAt(NOON).reserve(agentWith(null, null));

        assertThat(booking.agentId()).isEqualTo(7L);
        assertThat(booking.concurrentLimit()).isEqualTo(5);
        assertThat(booking.dailyLimit()).isEqualTo(200);
        assertThat(booking.reservationId()).isNotBlank();
        verify(counter).book(booking);
    }

    @Test
    void theAgentsOwnLimitsWinOverTheDefault() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.BOOKED);

        WidgetCallBooking booking = gateAt(NOON).reserve(agentWith(8, 50));

        assertThat(booking.concurrentLimit()).isEqualTo(8);
        assertThat(booking.dailyLimit()).isEqualTo(50);
    }

    @Test
    void theDayIsTheAgentsCalendarDayInTashkent() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.BOOKED);

        // 20:00 UTC is already one in the morning of the next day in Tashkent.
        WidgetCallBooking booking = gateAt(Instant.parse("2026-09-07T20:00:00Z")).reserve(agentWith(null, null));

        assertThat(booking.day()).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void fullLinesAreRefusedAsBusy() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.CONCURRENT_LIMIT_REACHED);

        assertThatThrownBy(() -> gateAt(NOON).reserve(agentWith(2, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 concurrent calls");
    }

    @Test
    void aSpentDayIsRefused() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.DAILY_LIMIT_REACHED);

        assertThatThrownBy(() -> gateAt(NOON).reserve(agentWith(null, 3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("daily call limit (3)");
    }

    @Test
    void anUnreachableCounterRefusesTheCallInsteadOfLettingItThrough() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.UNAVAILABLE);

        assertThatThrownBy(() -> gateAt(NOON).reserve(agentWith(null, null)))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("call-limit store is unavailable");
    }

    @Test
    void aBookingThatWasNotUsedIsHandedBack() {
        when(counter.book(any())).thenReturn(WidgetBookingResult.BOOKED);
        WidgetCallGate gate = gateAt(NOON);
        WidgetCallBooking booking = gate.reserve(agentWith(null, 1));

        gate.release(booking);

        verify(counter).release(booking);
    }

    private WidgetCallGate gateAt(Instant now) {
        return new WidgetCallGate(Clock.fixed(now, TASHKENT), counter);
    }

    private static AiAgent agentWith(Integer concurrent, Integer daily) {
        return AiAgentFixtures.agent(7L, 1L, 3L, "uz-UZ", "nigora")
                .withLimits(new AiAgentLimits(null, null, concurrent, daily));
    }
}
