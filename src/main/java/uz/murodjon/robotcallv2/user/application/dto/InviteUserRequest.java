package uz.murodjon.robotcallv2.user.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Email @Size(max = 190) String email,
        @NotNull Long roleId
) {
}
