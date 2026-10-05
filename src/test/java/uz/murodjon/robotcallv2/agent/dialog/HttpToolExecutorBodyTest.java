package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A nested request body from a JSON Schema (UNIVERSAL-OPERATOR-PLAN B.1): the model is asked
 * for the fields it fills, and the request carries those as it sent them plus the fields
 * the configuration fills — nothing more.
 */
class HttpToolExecutorBodyTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToolSchemas toolSchemas = new ToolSchemas(JSON);
    private final ToolRequestTemplates toolRequestTemplates = new ToolRequestTemplates(JSON);

    /** An order: lines and a street the model fills; a channel, the phone and a token it does not. */
    private static final Map<String, Object> ORDER_SCHEMA = Map.of(
            "type", "object",
            "required", List.of("items", "phone"),
            "$schema", "https://json-schema.org/draft/2020-12/schema",
            "properties", Map.of(
                    "items", Map.of("type", "array", "description", "buyurtma qatorlari",
                            "items", Map.of("type", "object", "properties", Map.of(
                                    "sku", Map.of("type", "string"),
                                    "qty", Map.of("type", "integer")))),
                    "address", Map.of("type", "object", "properties", Map.of(
                            "street", Map.of("type", "string"),
                            "channel", Map.of("type", "string", "x-source", "static", "x-value", "voice"))),
                    "phone", Map.of("type", "string", "x-source", "variable", "x-value", "phone"),
                    "token", Map.of("type", "string", "x-source", "secret", "x-value", "SHOP_TOKEN")));

    @Test
    void theModelIsAskedOnlyForTheFieldsItFills() {
        JsonNode schema = toolSchemas.schemaForModel(JSON.valueToTree(ORDER_SCHEMA));

        assertThat(schema.path("properties").has("items")).isTrue();
        assertThat(schema.path("properties").has("phone")).isFalse();
        assertThat(schema.path("properties").has("token")).isFalse();
        assertThat(schema.path("properties").path("address").path("properties").has("street")).isTrue();
        assertThat(schema.path("properties").path("address").path("properties").has("channel")).isFalse();
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(name -> required.add(name.asText()));
        assertThat(required).containsExactly("items");
        // A keyword a function declaration does not take would get the whole request refused.
        assertThat(schema.toString()).doesNotContain("x-source").doesNotContain("$schema");
    }

    @Test
    void theBodyIsTheNestedArgumentsPlusTheConfiguredFields() throws Exception {
        Map<String, Object> args = Map.of(
                "items", List.of(Map.of("sku", "A1", "qty", 2), Map.of("sku", "B7", "qty", 1)),
                "address", Map.of("street", "Chilonzor 5"),
                "invented", "not in the schema");

        String body = toolRequestTemplates.buildSchemaBody(ORDER_SCHEMA, Map.of("phone", "+998901234567"), args,
                Map.of("SHOP_TOKEN", "t0k3n"));

        assertThat(JSON.readTree(body)).isEqualTo(JSON.readTree("""
                {"items": [{"sku": "A1", "qty": 2}, {"sku": "B7", "qty": 1}],
                 "address": {"street": "Chilonzor 5", "channel": "voice"},
                 "phone": "+998901234567",
                 "token": "t0k3n"}
                """));
    }

    /** A field the configuration fills is never the model's, whatever it sends for it. */
    @Test
    void aConfiguredFieldIgnoresWhatTheModelSent() throws Exception {
        Map<String, Object> args = Map.of("items", List.of(), "token", "{{secrets.OTHER}}", "phone", "+000");

        JsonNode body = JSON.readTree(toolRequestTemplates.buildSchemaBody(ORDER_SCHEMA,
                Map.of("phone", "+998901234567"), args, Map.of("SHOP_TOKEN", "t0k3n", "OTHER", "leak")));

        assertThat(body.path("token").asText()).isEqualTo("t0k3n");
        assertThat(body.path("phone").asText()).isEqualTo("+998901234567");
    }

    @Test
    void aValueTheCallDoesNotHaveIsLeftOut() throws Exception {
        JsonNode body = JSON.readTree(toolRequestTemplates.buildSchemaBody(ORDER_SCHEMA, Map.of(),
                Map.of("items", List.of()), Map.of()));

        assertThat(body.has("phone")).isFalse();
        assertThat(body.has("token")).isFalse();
    }
}
