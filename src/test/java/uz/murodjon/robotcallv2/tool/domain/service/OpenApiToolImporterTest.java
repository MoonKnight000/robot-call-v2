package uz.murodjon.robotcallv2.tool.domain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.OpenApiOperation;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolAuthType;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OpenApiToolImporterTest {

    private static final String SERVER = "https://eu.shop.example/api";

    private static final String SPEC = """
            {
              "openapi": "3.0.3",
              "info": {"title": "Shop API"},
              "servers": [{"url": "https://{region}.shop.example/api/", "variables": {"region": {"default": "eu"}}}],
              "security": [{"bearer": []}],
              "paths": {
                "/orders/{id}": {
                  "parameters": [{"name": "id", "in": "path", "required": true, "description": "Order id", "schema": {"type": "string"}}],
                  "get": {"operationId": "getOrder", "summary": "Order status",
                          "parameters": [{"$ref": "#/components/parameters/Expand"}, {"name": "X-Trace", "in": "header"}]},
                  "delete": {"operationId": "cancelOrder"}
                },
                "/orders": {
                  "post": {"operationId": "createOrder", "summary": "Create an order",
                           "security": [{"cc": ["orders"]}],
                           "requestBody": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/NewOrder"}}}}}
                },
                "/uploads": {
                  "post": {"operationId": "upload",
                           "requestBody": {"content": {"multipart/form-data": {"schema": {"type": "object"}}}}}
                },
                "/catalog": {
                  "get": {"summary": "Catalog", "security": [{"key": []}],
                          "parameters": [{"name": "q", "in": "query", "schema": {"type": "string", "enum": ["a", "b"]}}]}
                }
              },
              "components": {
                "parameters": {"Expand": {"name": "expand", "in": "query", "schema": {"type": "boolean"}}},
                "schemas": {
                  "NewOrder": {"type": "object", "required": ["id", "items"], "properties": {
                    "id": {"type": "string", "readOnly": true},
                    "items": {"type": "array", "items": {"$ref": "#/components/schemas/Line"}}}},
                  "Line": {"type": "object", "properties": {"sku": {"type": "string"}, "qty": {"type": "integer"}}}
                },
                "securitySchemes": {
                  "bearer": {"type": "http", "scheme": "bearer"},
                  "key": {"type": "apiKey", "in": "query", "name": "api_key"},
                  "cc": {"type": "oauth2", "flows": {"clientCredentials": {"tokenUrl": "/oauth/token", "scopes": {"orders": ""}}}}
                }
              }
            }
            """;

    private final Map<String, Object> document = parse(SPEC);

    @Test
    void theServerComesWithItsVariablesFilledIn() {
        assertThat(OpenApiToolImporter.findServerUrl(document, null, null)).isEqualTo(SERVER);
        assertThat(OpenApiToolImporter.findServerUrl(document, "https://other.example/v2/", null))
                .isEqualTo("https://other.example/v2");
    }

    @Test
    void aRelativeServerIsReadAgainstTheDocumentsOwnAddress() {
        Map<String, Object> relative = parse("{\"openapi\": \"3.1.0\", \"servers\": [{\"url\": \"/v1\"}], \"paths\": {}}");

        assertThat(OpenApiToolImporter.findServerUrl(relative, null, "https://shop.example/docs/openapi.json"))
                .isEqualTo("https://shop.example/v1");
        assertThat(OpenApiToolImporter.findServerUrl(relative, null, null)).isNull();
    }

    @Test
    void pathAndQueryParametersBecomeTheModelsArguments() {
        Tool tool = operation("getOrder").tool();

        assertThat(tool.http().url()).isEqualTo(SERVER + "/orders/{id}");
        assertThat(tool.http().method()).isEqualTo("GET");
        assertThat(tool.effect()).isEqualTo(ToolEffect.READ);
        assertThat(tool.http().pathParams()).singleElement().satisfies(parameter -> {
            assertThat(parameter.name()).isEqualTo("id");
            assertThat(parameter.required()).isTrue();
            assertThat(parameter.description()).isEqualTo("Order id");
        });
        // The header parameter is the configuration's, not the model's.
        assertThat(tool.http().queryParams()).extracting(ToolParamConfig::name).containsExactly("expand");
    }

    @Test
    void aBearerSchemeReadsTheTokenFromASecretNamedAfterTheTitle() {
        OpenApiOperation operation = operation("getOrder");

        assertThat(operation.tool().http().headers()).containsExactly(
                new ToolKeyValuePair("Authorization", "Bearer {{secrets.SHOP_API_TOKEN}}", null, null));
        assertThat(operation.secretKeys()).containsExactly("SHOP_API_TOKEN");
    }

    @Test
    void aJsonBodyIsInlinedWithoutTheFieldsTheServerFills() {
        Tool tool = operation("createOrder").tool();

        Map<String, Object> schema = tool.http().bodySchema();
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(map(schema.get("properties"))).containsOnlyKeys("items");
        assertThat(schema.get("required")).isEqualTo(List.of("items"));
        Map<String, Object> items = map(map(schema.get("properties")).get("items"));
        assertThat(map(map(items.get("items")).get("properties"))).containsOnlyKeys("sku", "qty");
        assertThat(tool.effect()).isEqualTo(ToolEffect.WRITE);
        assertThatCode(() -> ToolValidator.validateBody(tool.http().body(), tool.http().bodySchema())).doesNotThrowAnyException();
    }

    @Test
    void anOauthClientCredentialsSchemeBecomesTheToolsOwnClient() {
        OpenApiOperation operation = operation("createOrder");

        assertThat(operation.tool().http().auth().type()).isEqualTo(ToolAuthType.OAUTH2_CLIENT_CREDENTIALS);
        assertThat(operation.tool().http().auth().tokenUrl()).isEqualTo("https://eu.shop.example/oauth/token");
        assertThat(operation.tool().http().auth().scope()).isEqualTo("orders");
        assertThat(operation.secretKeys()).containsExactly("SHOP_API_CLIENT_ID", "SHOP_API_CLIENT_SECRET");
        assertThatCode(() -> ToolValidator.validateAuth(operation.tool().http().auth())).doesNotThrowAnyException();
    }

    @Test
    void anApiKeyInTheQueryGoesIntoTheAddressAndAnUnnamedOperationGetsAnId() {
        OpenApiOperation operation = operation("get_catalog");

        assertThat(operation.tool().http().url()).isEqualTo(SERVER + "/catalog?api_key={{secrets.SHOP_API_API_KEY}}");
        assertThat(operation.tool().http().queryParams()).singleElement()
                .satisfies(parameter -> assertThat(parameter.allowedValues()).containsExactly("a", "b"));
    }

    @Test
    void aDeleteIsNeverOfferedToTheModel() {
        assertThat(operation("cancelOrder").tool().effect()).isEqualTo(ToolEffect.DESTRUCTIVE);
    }

    @Test
    void aFormBodyCannotBecomeATool() {
        OpenApiOperation operation = operation("upload");

        assertThat(operation.tool()).isNull();
        assertThat(operation.unsupportedReason()).contains("not JSON");
    }

    @Test
    void theGivenPrefixNamesTheSecrets() {
        List<OpenApiOperation> operations = OpenApiToolImporter.readOperations(document, SERVER, "crm");

        assertThat(operations.getFirst().secretKeys()).containsExactly("CRM_TOKEN");
    }

    @Test
    void aSwagger2DocumentIsRefused() {
        Map<String, Object> swagger = parse("{\"swagger\": \"2.0\", \"paths\": {}}");

        assertThatThrownBy(() -> OpenApiToolImporter.readOperations(swagger, SERVER, null))
                .isInstanceOf(ValidationException.class);
    }

    private OpenApiOperation operation(String operationId) {
        return OpenApiToolImporter.readOperations(document, SERVER, null).stream()
                .filter(operation -> operation.operationId().equals(operationId))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> parse(String json) {
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
