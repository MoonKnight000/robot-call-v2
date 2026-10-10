package uz.murodjon.robotcallv2.dialer.infrastructure.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.dialer.application.port.input.PhoneNumberScrubberUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.output.PhoneNumberScrubberPort;
import uz.murodjon.robotcallv2.dialer.domain.entity.ScrubResult;
import uz.murodjon.robotcallv2.dialer.domain.service.PhoneNumberScrubber;

@Component
public class DefaultPhoneNumberScrubberAdapter implements PhoneNumberScrubberPort, PhoneNumberScrubberUseCase {

    private final PhoneNumberScrubber scrubber = new PhoneNumberScrubber();

    @Override
    public ScrubResult scrub(String phone) {
        return scrubber.scrub(phone);
    }
}
