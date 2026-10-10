package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.domain.entity.ScrubResult;

/**
 * UseCase to scrub and normalize phone numbers against national numbering plans.
 */
public interface PhoneNumberScrubberUseCase {

    /**
     * Cleans, formats, checks the length, mobile prefix, and carrier of a raw phone number.
     *
     * @param raw the input phone string in any format
     * @return the validation and normalization result
     */
    ScrubResult scrub(String raw);
}
