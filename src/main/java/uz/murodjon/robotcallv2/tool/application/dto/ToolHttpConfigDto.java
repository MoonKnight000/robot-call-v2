package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolAuth;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;

import java.util.List;
import java.util.Map;

/**
 * {@link ToolHttpConfig} as the tool endpoints read and write it. On a create a null field
 * takes the default; on an update it keeps the tool's value.
 *
 * @param auth on an update, null keeps the tool's auth and one without a {@code type}
 *             ({@code {}}) removes it
 */
public record ToolHttpConfigDto(
        @Size(max = 1000, message = "http.url must be at most 1000 characters")
        String url,
        String method,
        List<ToolKeyValuePair> headers,
        List<ToolParamConfig> body,
        List<ToolParamConfig> queryParams,
        List<ToolParamConfig> pathParams,
        Map<String, Object> bodySchema,
        Integer responseTimeoutSecs,
        @Min(0) @Max(Tool.MAX_RETRY_ATTEMPTS) Integer retryAttempts,
        ToolAuth auth,
        Boolean waitForResult,
        @Pattern(regexp = "^([A-Za-z_][A-Za-z0-9_]{0,63})?$", message = "http.statusVariable must be a name of letters, digits and _")
        String statusVariable
) {
    public static ToolHttpConfigDto of(ToolHttpConfig http) {
        return new ToolHttpConfigDto(http.url(), http.method(), http.headers(), http.body(), http.queryParams(),
                http.pathParams(), http.bodySchema(), http.responseTimeoutSecs(), http.retryAttempts(), http.auth(),
                http.waitForResult(), http.statusVariable());
    }
}
