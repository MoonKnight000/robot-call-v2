package uz.murodjon.robotcallv2.widget.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.widget.application.port.output.WidgetCallCounterPort;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetCallBooking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Holds an agent's own call limits against the one place a stranger can start a call: the
 * embed script on a public page.
 *
 * <p>{@code concurrentCallsLimit} and {@code dailyCallsLimit} are the agent's settings and
 * apply to every kind of call in principle; enforced here because this is where they are
 * load-bearing. A campaign dials numbers an operator chose, at a pace the dialer sets — a
 * widget is a button on the open internet, and without a ceiling one page of traffic spends
 * a company's recognition, model and synthesis budget in an afternoon.
 *
 * <p>Counted in one shared store ({@link WidgetCallCounterPort}), so every instance sees
 * the same numbers, and checked and booked in one atomic step, so a burst cannot all pass
 * the check before any of it is counted. While the store is unreachable the call is
 * refused: this endpoint is open to anything that can send an HTTP request, and a ceiling
 * that switches itself off during an outage is no ceiling. The daily count follows the
 * agent's calendar day in Tashkent, and a booked session stops holding its line once it
 * expires, so a visitor who never connects does not hold one for ever.
 */
@Component
public class WidgetCallGate {

    private static final Logger log = LoggerFactory.getLogger(WidgetCallGate.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    /**
     * What an agent with no limit of its own gets on this path. Unset cannot mean
     * unlimited here: the widget key is printed in the customer's page and the origin
     * header is only a header, so the endpoint is reachable by anything that can make an
     * HTTP request. These are set high enough that a real site's traffic never meets
     * them and low enough that a script cannot spend a night's budget before anyone
     * looks — an operator who wants more says so on the agent.
     */
    private static final int DEFAULT_CONCURRENT_CALLS_LIMIT = 5;
    private static final int DEFAULT_DAILY_CALLS_LIMIT = 200;

    private final Clock clock;
    private final WidgetCallCounterPort counter;

    public WidgetCallGate(Clock clock, WidgetCallCounterPort counter) {
        this.clock = clock;
        this.counter = counter;
    }

    /**
     * Books a call against the agent's limits, or refuses it. An agent that configured no
     * limit of its own falls back to {@link #DEFAULT_CONCURRENT_CALLS_LIMIT} and
     * {@link #DEFAULT_DAILY_CALLS_LIMIT} rather than to no ceiling at all.
     *
     * <p>The booking counts from here on. When the session it was made for cannot be
     * prepared, hand it back with {@link #release} — a call that never happened must not
     * spend the allowance.
     */
    public WidgetCallBooking reserve(AiAgent agent) {
        Instant now = clock.instant();
        int concurrentLimit = limitOrDefault(agent.limits().concurrentCallsLimit(), DEFAULT_CONCURRENT_CALLS_LIMIT);
        int dailyLimit = limitOrDefault(agent.limits().dailyCallsLimit(), DEFAULT_DAILY_CALLS_LIMIT);
        WidgetCallBooking booking = new WidgetCallBooking(agent.id(), UUID.randomUUID().toString(), now,
                LocalDate.ofInstant(now, ZONE), concurrentLimit, dailyLimit);

        return switch (counter.book(booking)) {
            case BOOKED -> booking;
            case CONCURRENT_LIMIT_REACHED -> {
                log.info("Widget call refused: agent {} is at its concurrent limit of {}", agent.id(), concurrentLimit);
                throw new ConflictException(ErrorCode.AGENT_WIDGET_BUSY, concurrentLimit);
            }
            case DAILY_LIMIT_REACHED -> {
                log.info("Widget call refused: agent {} has used its daily limit of {}", agent.id(), dailyLimit);
                throw new ConflictException(ErrorCode.AGENT_WIDGET_DAILY_LIMIT_REACHED, dailyLimit);
            }
            case UNAVAILABLE -> {
                log.warn("Widget call refused: the call-limit store is unreachable (agent {})", agent.id());
                throw new ExternalServiceException(ErrorCode.AGENT_WIDGET_LIMIT_UNAVAILABLE, "redis");
            }
        };
    }

    /** Hands back a booking whose session could not be prepared. */
    public void release(WidgetCallBooking booking) {
        counter.release(booking);
    }

    /** The agent's own limit when it set one, otherwise the ceiling this path insists on. */
    private static int limitOrDefault(Integer configured, int fallback) {
        return configured != null && configured > 0 ? configured : fallback;
    }
}
