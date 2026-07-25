package uz.murodjon.uysotvoice.agent.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.uysotvoice.agent.dialog.CallSummary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Posts the call summary back to the Uysot CRM as a note (Stage 9). Disabled by
 * default; when off (or on any error) {@link #postNote} returns {@code null} and the
 * result is still persisted locally without a {@code crm_note_id}.
 */
@Component
public class CrmClient {

    private static final Logger log = LoggerFactory.getLogger(CrmClient.class);

    private final CrmProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public CrmClient(CrmProperties props) {
        this.props = props;
    }

    /**
     * Create a CRM note for {@code clientId} from {@code summary}; returns the note
     * id or {@code null} if the CRM is disabled/unconfigured or the call fails.
     */
    public Long postNote(long clientId, CallSummary summary) {
        if (!props.enabled() || props.baseUrl() == null || props.baseUrl().isBlank() || summary == null) {
            return null;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("clientId", clientId);
            body.put("source", "voice-agent");
            body.put("note", summary.summary() != null ? summary.summary() : "");
            if (summary.reasonCode() != null) {
                body.put("reasonCode", summary.reasonCode().name());
            }
            if (summary.promisedDate() != null) {
                body.put("promisedDate", summary.promisedDate().toString());
            }
            if (summary.promisedAmount() != null) {
                body.put("promisedAmount", summary.promisedAmount());
            }

            String base = props.baseUrl().endsWith("/") ? props.baseUrl() : props.baseUrl() + "/";
            String path = props.notePath() != null ? props.notePath() : "api/notes";
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(base + (path.startsWith("/") ? path.substring(1) : path)))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (props.apiToken() != null && !props.apiToken().isBlank()) {
                req.header("Authorization", "Bearer " + props.apiToken());
            }

            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("CRM note HTTP {}: {}", resp.statusCode(), resp.body());
                return null;
            }
            JsonNode json = mapper.readTree(resp.body());
            Long id = json.hasNonNull("id") ? json.get("id").asLong() : null;
            log.info("CRM note created for client {} (noteId={})", clientId, id);
            return id;
        } catch (Exception e) {
            log.warn("CRM note post failed for client {}: {}", clientId, e.getMessage());
            return null;
        }
    }
}
