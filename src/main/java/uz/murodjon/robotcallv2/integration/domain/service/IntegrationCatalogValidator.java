package uz.murodjon.robotcallv2.integration.domain.service;

import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Pure rules for the integration catalog: its entries and its categories. */
public final class IntegrationCatalogValidator {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final int MAX_NAME_LENGTH = 100;

    private IntegrationCatalogValidator() {
    }

    /** The key as stored: trimmed and upper-cased; rejected unless 2 to 32 letters, digits or _ and starting with a letter. */
    public static String normalizeKey(String key) {
        String normalized = key == null ? "" : key.strip().toUpperCase(Locale.ROOT);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(ErrorCode.INTEGRATION_KEY_INVALID);
        }
        return normalized;
    }

    /** The methods as stored: no repeats, the order kept; at least one. */
    public static List<IntegrationAuthMethod> normalizeAuthMethods(List<IntegrationAuthMethod> authMethods) {
        List<IntegrationAuthMethod> distinct = authMethods == null ? List.of()
                : authMethods.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw new ValidationException(ErrorCode.INTEGRATION_AUTH_METHODS_REQUIRED);
        }
        return distinct;
    }

    /** The name as stored: trimmed, not blank and at most 100 characters. */
    public static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(ErrorCode.INTEGRATION_NAME_INVALID, MAX_NAME_LENGTH);
        }
        return normalized;
    }
}
