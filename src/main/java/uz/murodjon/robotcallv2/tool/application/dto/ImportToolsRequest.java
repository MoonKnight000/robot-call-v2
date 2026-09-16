package uz.murodjon.robotcallv2.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * An OpenAPI 3 document to import tools from (UNIVERSAL-OPERATOR-PLAN B.2) — by address or as
 * text, exactly one of the two.
 *
 * @param operationIds which operations become tools; ignored when only listing them
 * @param baseUrl      where the tools send their requests, instead of the document's server
 * @param secretPrefix the start of the secret keys the tools read ({@code SHOP} → {@code SHOP_TOKEN});
 *                     null derives it from the document's title
 */
public record ImportToolsRequest(
        @Size(max = 2048) String openApiUrl,
        @Size(max = 5_000_000) String openApiSpec,
        @Size(max = 100) List<@NotBlank @Size(max = 200) String> operationIds,
        @Size(max = 500) String baseUrl,
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_]*$") String secretPrefix
) {
}
