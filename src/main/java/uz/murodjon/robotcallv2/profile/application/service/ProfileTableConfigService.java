package uz.murodjon.robotcallv2.profile.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.profile.application.port.input.ProfileTableConfigUseCase;
import uz.murodjon.robotcallv2.profile.application.port.output.TableConfigRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

@Service
public class ProfileTableConfigService implements ProfileTableConfigUseCase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TableConfigRepository repository;
    private final CurrentUser currentUser;

    public ProfileTableConfigService(TableConfigRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Override
    public JsonNode find(String key) {
        String json = repository.find(requireUserId(), requireKey(key));
        return json == null ? null : readValue(json);
    }

    @Override
    public JsonNode update(long companyId, String key, JsonNode value) {
        String json = value == null || value.isNull() ? null : value.toString();
        String saved = repository.save(companyId, requireUserId(), requireKey(key), json);
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
