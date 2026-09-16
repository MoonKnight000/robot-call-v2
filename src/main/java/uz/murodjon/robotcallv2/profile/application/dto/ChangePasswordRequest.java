package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.shared.util.PasswordRules;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
        String newPassword
) {
}
