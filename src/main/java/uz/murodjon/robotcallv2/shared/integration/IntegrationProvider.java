package uz.murodjon.robotcallv2.shared.integration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The key of an integration in the platform's catalog (the {@code integration_provider} table), such as
 * {@code UYSOT} or {@code ESKIZ}. The platform admin adds and edits catalog entries, so a provider is
 * not a fixed list: the company feature (which integrations are switched off for a company) and the
 * integration feature both need the same key, hence here and not in either.
 *
 * <p>Only the providers a connector in the code names are constants. Whether a key exists in the
 * catalog is the catalog's to say; this type only guarantees its shape.
 */
public record IntegrationProvider(String key) {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    public static final IntegrationProvider UYSOT = new IntegrationProvider("UYSOT");
    public static final IntegrationProvider ESKIZ = new IntegrationProvider("ESKIZ");
    public static final IntegrationProvider AMOCRM = new IntegrationProvider("AMOCRM");
    public static final IntegrationProvider KOMMO = new IntegrationProvider("KOMMO");
    public static final IntegrationProvider BITRIX24 = new IntegrationProvider("BITRIX24");

    public IntegrationProvider {
        key = key == null ? null : key.strip().toUpperCase(Locale.ROOT);
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ValidationException(ErrorCode.INTEGRATION_KEY_INVALID);
        }
    }

    /** What JSON, a path variable and a query parameter carry: the key. */
    @JsonCreator
    public static IntegrationProvider valueOf(String key) {
        return new IntegrationProvider(key);
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** The key, for code that read it as an enum's {@code name()}. */
    public String name() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
