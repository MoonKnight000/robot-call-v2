package uz.murodjon.robotcallv2.mcp.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpAuthType;

/**
 * @param url          the server's endpoint; HTTPS only, and never an address on this network
 * @param secretKey    the name of the company secret holding the bearer token, for
 *                     {@code BEARER} auth — the token itself is never sent here
 * @param allowWrites  whether tools the server does not mark read-only may be offered to
 *                     the model at all; leave false unless the company means it
 */
public record McpConnectionRequest(
        @NotBlank(message = "Ulanish nomi kiritilishi shart")
        @Size(max = 128, message = "Nom 128 belgidan oshmasin")
        String name,

        @NotBlank(message = "URL kiritilishi shart")
        @Size(max = 500, message = "URL 500 belgidan oshmasin")
        String url,

        McpAuthType authType,

        @Size(max = 128, message = "secretKey 128 belgidan oshmasin")
        String secretKey,

        Boolean allowWrites
) {
}
