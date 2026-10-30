package uz.murodjon.robotcallv2.integration.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for a platform-defined HTTP tool. The point of most of them is that the admin
 * describes <em>what</em> to ask the integration, never <em>where</em> to send it or with
 * which credentials: the host and the token come from the company's connection.
 */
public final class IntegrationToolValidator {

    private static final Pattern KEY = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final Pattern MODEL_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{2,63}$");

    private IntegrationToolValidator() {
    }

    public static void validateKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_KEY_INVALID);
        }
    }

    /** @param takenNames the names the model already knows other tools of this integration by */
    public static void validateModelName(String name, Set<String> takenNames) {
        if (name == null || !MODEL_NAME.matcher(name).matches() || takenNames.contains(name)) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_NAME_INVALID, name);
        }
    }

    /**
     * The template's request may only name a path under the integration's host, and may not
     * carry headers or its own authentication: those would be a second way to reach a
     * different host, or to send a different credential.
     */
    public static void validateRequest(Tool template) {
        if (template.http().headers() != null && !template.http().headers().isEmpty() || template.http().auth() != null) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_HTTP_LOCKED);
        }
        String path = template.http().url();
        if (path == null || path.isBlank() || path.contains("://") || path.startsWith("/") || path.startsWith("{{")
                || path.contains("..") || path.contains("\\") || path.contains("@") || path.contains("//")
                || path.chars().anyMatch(Character::isWhitespace)) {
            throw new ValidationException(ErrorCode.INTEGRATION_TOOL_PATH_INVALID);
        }
    }
}
