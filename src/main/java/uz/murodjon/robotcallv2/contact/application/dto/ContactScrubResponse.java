package uz.murodjon.robotcallv2.contact.application.dto;

import java.util.List;

public record ContactScrubResponse(
        int total,
        int validCount,
        int invalidCount,
        int dncCount,
        List<ScrubbedPhoneItem> items
) {
}
