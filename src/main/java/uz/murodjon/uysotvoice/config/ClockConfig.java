package uz.murodjon.uysotvoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Supplies the {@link Clock} the dialer reads the time from.
 *
 * <p>The dial window and the retry schedule are decisions about the time of day, and
 * calling {@code LocalTime.now()} inside them makes those decisions untestable: the
 * behaviour at 08:59 versus 09:01, or on a Sunday, can only be checked by waiting for it.
 * Injecting the clock lets a test fix the moment instead.
 */
@Configuration
public class ClockConfig {

    /** System clock in the JVM's zone — the zone the dial window is expressed in. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
