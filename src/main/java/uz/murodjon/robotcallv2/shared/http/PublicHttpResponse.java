package uz.murodjon.robotcallv2.shared.http;

import java.nio.charset.StandardCharsets;

/**
 * The answer to a {@link PublicHttpRequest}.
 *
 * @param statusCode the HTTP status
 * @param body       the body, at most the request's {@code maxResponseBytes}; empty when there was none
 * @param truncated  whether the far end sent more than that — the rest was not read
 */
public record PublicHttpResponse(int statusCode, byte[] body, boolean truncated) {

    /** The body as UTF-8 text. */
    public String readBodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
