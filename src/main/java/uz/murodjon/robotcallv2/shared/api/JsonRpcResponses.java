package uz.murodjon.robotcallv2.shared.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

/**
 * The HTTP side of a JSON-RPC answer on an MCP Streamable HTTP endpoint: a response goes back
 * as {@code 200 application/json}, and a message that needs none — a notification — as
 * {@code 202 Accepted} with no body, as the transport requires. Not wrapped in
 * {@link ResponseData}: an MCP client reads the JSON-RPC envelope and nothing else.
 */
public final class JsonRpcResponses {

    private JsonRpcResponses() {
    }

    public static ResponseEntity<String> of(Optional<String> response) {
        return response
                .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.ACCEPTED).build());
    }
}
