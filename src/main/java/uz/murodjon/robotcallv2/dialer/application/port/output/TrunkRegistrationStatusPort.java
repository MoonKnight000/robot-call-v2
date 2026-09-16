package uz.murodjon.robotcallv2.dialer.application.port.output;

/**
 * Whether the SIP registration behind a trunk is known to be down. Answered by the voice
 * pipeline's registration monitor ({@code agent/ami}); the dialer sees only this port, so it
 * does not depend on the pipeline that depends on it.
 */
public interface TrunkRegistrationStatusPort {

    /**
     * Whether Asterisk last reported {@code registration} as anything but registered. Unknown
     * is not down: the dialer must not stop dialling because it cannot see.
     */
    boolean isKnownDown(String registration);
}
