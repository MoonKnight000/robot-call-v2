package uz.murodjon.robotcallv2.contact.application.dto;

import uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus;

public record ScrubbedPhoneItem(
        String rawPhone,
        String normalizedPhone,
        boolean valid,
        ScrubStatus status,
        String carrierOrRegion,
        String reason,
        boolean inDoNotCall
) {
}
