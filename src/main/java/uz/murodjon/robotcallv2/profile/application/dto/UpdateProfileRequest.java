package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 190) String email,
        @Pattern(regexp = PhoneNumbers.E164_REGEX) String phone,
        @Size(max = 120) String position
) {
}
