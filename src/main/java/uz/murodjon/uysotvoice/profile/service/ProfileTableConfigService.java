package uz.murodjon.uysotvoice.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.profile.repository.TableConfigRepository;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

/**
 * "Ustunlar ⚙" and other free-form, per-table UI preferences (backend-uchun-talablar.md
 * §1, API-REQUIREMENTS §4) — one JSON blob per (user, {@code configKey}); both the key
 * namespace and the JSON shape are the frontend's own concern, the backend only stores
 * and returns them as-is.
 */
@Service
public class ProfileTableConfigService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TableConfigRepository repo;
    private final CurrentUser currentUser;

    public ProfileTableConfigService(TableConfigRepository repo, CurrentUser currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    public JsonNode find(String key) {
        String json = repo.find(requireUserId(), requireKey(key));
        return json == null ? null : readValue(json);
    }

    public JsonNode update(String key, JsonNode value) {
        String json = value == null || value.isNull() ? null : value.toString();
        String saved = repo.save(requireUserId(), requireKey(key), json);
        return saved == null ? null : readValue(saved);
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException(ErrorCode.PROFILE_TABLE_CONFIG_KEY_BLANK);
        }
        if (key.length() > 100) {
            throw new ValidationException(ErrorCode.PROFILE_TABLE_CONFIG_KEY_TOO_LONG);
        }
        return key;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }

    private static JsonNode readValue(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt table config JSON: " + e.getMessage(), e);
        }
    }
}
