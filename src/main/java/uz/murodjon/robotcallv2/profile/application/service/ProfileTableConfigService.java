package uz.murodjon.robotcallv2.profile.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.profile.application.port.input.ProfileTableConfigUseCase;
import uz.murodjon.robotcallv2.profile.application.port.output.TableConfigRepository;
import uz.murodjon.robotcallv2.profile.domain.entity.TableConfig;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.security.CurrentUser;

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
        TableConfig config = repository.findByUserIdAndConfigKey(requireUserId(), requireKey(key));
        return config == null ? null : readValue(config.configValue());
    }

    @Override
    public JsonNode update(long companyId, String key, JsonNode value) {
        long userId = requireUserId();
        String configKey = requireKey(key);
        if (value == null || value.isNull()) {
            repository.deleteByUserIdAndConfigKey(userId, configKey);
            return null;
        }
        TableConfig saved = repository.upsert(companyId, TableConfig.of(userId, configKey, value.toString()));
        return readValue(saved.configValue());
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
            throw new ConflictException(ErrorCode.PROFILE_TABLE_CONFIG_CORRUPT, e);
        }
    }
}
