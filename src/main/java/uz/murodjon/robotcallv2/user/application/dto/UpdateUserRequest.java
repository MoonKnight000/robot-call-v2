package uz.murodjon.robotcallv2.user.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.shared.util.PasswordRules;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

/**
 * An admin editing a user's account. Name, login and email are required; phone, position and SIP
 * extension are replaced as sent (null clears them). {@code newPassword} is optional — null
 * leaves the password alone. The role and status have their own endpoints.
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 190) String email,
        @Pattern(regexp = PhoneNumbers.E164_REGEX) String phone,
        @Size(max = 120) String position,
        @Pattern(regexp = "^\\d{2,8}$") String sipExtension,
        @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
        String newPassword
) {
}
