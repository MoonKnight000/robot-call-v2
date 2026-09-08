package uz.murodjon.robotcallv2.widget.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * <p>Counted in this process, not in the database. The numbers are a safety ceiling rather
 * than billing: a second instance would let through its own allowance, which is the right
 * trade against a database round trip on the path a visitor is waiting on. The daily
 * counter resets on the agent's own calendar day in Tashkent, and a booked session is
 * released when it expires, so a visitor who never connects does not hold a line for ever.
 */
@Component
public class WidgetCallGate {

    private static final Logger log = LoggerFactory.getLogger(WidgetCallGate.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    /**
     * How long a booked session counts against the concurrency limit before it is assumed
     * abandoned. Matches the five minutes Asterisk keeps the pending session for: past
     * that the browser can no longer dial in, so the slot is genuinely free.
     */
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

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
    /** Agent id → the sessions it has booked that have not yet expired. */
    private final Map<Long, Map<String, Instant>> inFlight = new ConcurrentHashMap<>();
    /** Agent id → how many calls it has taken today, and which day that is. */
    private final Map<Long, DailyCount> daily = new ConcurrentHashMap<>();

    public WidgetCallGate(Clock clock) {
        this.clock = clock;
    }

    /**
     * Refuses the call if the agent is out of allowance. An agent that configured no
     * limit of its own falls back to {@link #DEFAULT_CONCURRENT_CALLS_LIMIT} and
     * {@link #DEFAULT_DAILY_CALLS_LIMIT} rather than to no ceiling at all.
     *
     * <p>Checks without counting; {@link #recordBooking} does the counting once the
     * session actually exists. The two steps together are not atomic, so a burst arriving
     * in the same instant can put one or two calls over the line. That is the right trade
     * for a ceiling whose job is to stop a runaway page, not to bill: counting here
     * instead would permanently consume the allowance of every call that then failed to
     * book.
     */
    public void checkCallAllowed(AiAgent agent) {
        Instant now = clock.instant();

        int concurrentLimit = limitOrDefault(agent.limits().concurrentCallsLimit(), DEFAULT_CONCURRENT_CALLS_LIMIT);
        if (inFlightCount(agent.id(), now) >= concurrentLimit) {
            log.info("Widget call refused: agent {} is at its concurrent limit of {}", agent.id(), concurrentLimit);
            throw new ConflictException(ErrorCode.AGENT_WIDGET_BUSY, concurrentLimit);
        }

        int dailyLimit = limitOrDefault(agent.limits().dailyCallsLimit(), DEFAULT_DAILY_CALLS_LIMIT);
        if (callsToday(agent.id(), now) >= dailyLimit) {
            log.info("Widget call refused: agent {} has used its daily limit of {}", agent.id(), dailyLimit);
            throw new ConflictException(ErrorCode.AGENT_WIDGET_DAILY_LIMIT_REACHED, dailyLimit);
        }
    }

    /** Counts a session that was actually booked, against both limits. */
    public void recordBooking(AiAgent agent, String sessionId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZONE);

        inFlight.computeIfAbsent(agent.id(), id -> new ConcurrentHashMap<>()).put(sessionId, now);
        daily.compute(agent.id(), (id, current) ->
                current == null || !current.day().equals(today)
                        ? new DailyCount(today, 1)
                        : new DailyCount(today, current.calls() + 1));
    }

    /** The agent's own limit when it set one, otherwise the ceiling this path insists on. */
    private static int limitOrDefault(Integer configured, int fallback) {
        return configured != null && configured > 0 ? configured : fallback;
    }

    /** Live sessions, with the abandoned ones swept out first. */
    private int inFlightCount(long agentId, Instant now) {
        Map<String, Instant> sessions = inFlight.get(agentId);
        if (sessions == null) {
            return 0;
        }
        sessions.values().removeIf(bookedAt -> bookedAt.isBefore(now.minus(SESSION_TTL)));
        return sessions.size();
    }

    /** Calls taken on the agent's own calendar day; yesterday's count reads as zero. */
    private int callsToday(long agentId, Instant now) {
        DailyCount count = daily.get(agentId);
        return count != null && count.day().equals(LocalDate.ofInstant(now, ZONE)) ? count.calls() : 0;
    }

    private record DailyCount(LocalDate day, int calls) {
    }
}
