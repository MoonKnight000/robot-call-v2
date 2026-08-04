package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/profile} body. {@code username} is not here — it is the login
 * identifier and stays fixed, same as everywhere else in the codebase (no
 * {@code UserRepository} method changes it). {@code email} is editable — no SSO
 * provider exists yet (Uysot OAuth, {@code POST /api/auth/uysot/callback}, is still a
 * stub), so UI-DESIGN §8.3's "o'zgarmas, agar SSO bo'lsa" carve-out has nothing to key
 * off today. The avatar is not here either — it only ever changes through {@code POST
 * /api/profile/avatar}, never as an arbitrary id a caller can set directly.
 */
public record UpdateProfileRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        String position
) {
}
