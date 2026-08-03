package uz.murodjon.uysotvoice.profile.dto;

import uz.murodjon.uysotvoice.user.enums.UserRole;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET/PUT /api/profile} — self-service "Umumiy" tab (UI-DESIGN §8.3).
 *
 * @param callColumns persisted "Ustunlar ⚙" choice for the calls table (§10.4/API-REQUIREMENTS
 *                    §4), or {@code null} if never saved — the frontend then falls back to its
 *                    own default set. Column keys are frontend-owned; the backend only stores
 *                    them.
 */
public record Profile(
        long id,
        String name,
        String username,
        String email,
        String phone,
        String position,
        String avatarUrl,
        UserRole role,
        long companyId,
        Instant lastLoginAt,
        Instant createdAt,
        List<String> callColumns
) {
}
