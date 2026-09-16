package uz.murodjon.robotcallv2.pack.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A business in its owner's own words, and optionally the API its agent should call
 * (UNIVERSAL-OPERATOR-PLAN E.1).
 *
 * @param business     what the company does, what the calls are for, the hours, the rules —
 *                     prose, not a form. The more concrete it is, the less the model invents
 * @param openApiUrl   the API document's address; with {@code openApiSpec}, exactly one of the two
 * @param openApiSpec  the document as text, for an API that is not published
 * @param baseUrl      where the tools send their requests, instead of the document's server
 * @param secretPrefix the start of the secret keys the tools read ({@code SHOP} → {@code SHOP_TOKEN})
 * @param language     the calls' language as a BCP-47 tag; null uses the company's own
 */
public record GeneratePackRequest(
        @NotBlank @Size(max = 4000) String business,
        @Size(max = 2048) String openApiUrl,
        @Size(max = 5_000_000) String openApiSpec,
        @Size(max = 500) String baseUrl,
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_]*$") String secretPrefix,
        @Size(max = 10) String language
) {

    /** Whether an API was given at all — without one the agent is a pack with no tools. */
    public boolean hasApi() {
        return (openApiUrl != null && !openApiUrl.isBlank()) || (openApiSpec != null && !openApiSpec.isBlank());
    }
}
