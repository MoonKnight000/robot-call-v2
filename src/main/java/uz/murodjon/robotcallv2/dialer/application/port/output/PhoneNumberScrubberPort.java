package uz.murodjon.robotcallv2.dialer.application.port.output;

import uz.murodjon.robotcallv2.dialer.domain.entity.ScrubResult;

/**
 * Output port for pre-call phone number validation and scrubbing.
 */
public interface PhoneNumberScrubberPort {

    /**
     * Scrubs and validates {@code phone} before origination.
     */
    ScrubResult scrub(String phone);
}
