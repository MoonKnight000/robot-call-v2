package uz.murodjon.robotcallv2.contact.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContactScrubRequest(
        @NotEmpty(message = "phones must not be empty")
        @Size(max = 5000, message = "cannot scrub more than 5000 phones in a single batch")
        List<String> phones
) {
}
