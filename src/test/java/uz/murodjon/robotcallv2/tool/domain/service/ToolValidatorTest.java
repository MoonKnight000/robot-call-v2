package uz.murodjon.robotcallv2.tool.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolValidatorTest {

    @Test
    void sortFieldsAreSpelledAsTheEntityProperty() {
        assertThat(ToolValidator.validateSortFields(Map.of("CREATED_AT", "DESC")))
                .containsExactly(Map.entry("createdAt", "DESC"));
        assertThat(ToolValidator.validateSortFields(Map.of("updatedAt", "ASC")))
                .containsExactly(Map.entry("updatedAt", "ASC"));
        assertThat(ToolValidator.validateSortFields(Map.of("name", "ASC")))
                .containsExactly(Map.entry("name", "ASC"));
    }

    /** Passed through raw, an unknown property reached Spring Data and came back as a 500. */
    @Test
    void anUnknownSortFieldIsAValidationError() {
        assertThatThrownBy(() -> ToolValidator.validateSortFields(Map.of("secretValue", "ASC")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void noSortIsFine() {
        assertThat(ToolValidator.validateSortFields(Map.of())).isEmpty();
    }

    // --- bodySchema (UNIVERSAL-OPERATOR-PLAN B.1) ---

    /** An order: lines the model fills, the caller's phone from the call. */
    private static final Map<String, Object> ORDER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "items", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                            "sku", Map.of("type", "string"),
                            "qty", Map.of("type", "integer")))),
                    "phone", Map.of("type", "string", "x-source", "variable")));

    private static ErrorCode codeOf(Runnable validation) {
        try {
            validation.run();
            return null;
        } catch (ValidationException e) {
            return e.code();
        }
    }

    @Test
    void aNestedBodySchemaIsAccepted() {
        assertThat(codeOf(() -> ToolValidator.validateBody(List.of(), ORDER_SCHEMA))).isNull();
    }

    @Test
    void aBodyIsDescribedOneWayNotBoth() {
        ToolParamConfig flat = new ToolParamConfig("phone", "string", null, null, null, null, true);
        assertThat(codeOf(() -> ToolValidator.validateBody(List.of(flat), ORDER_SCHEMA)))
                .isEqualTo(ErrorCode.TOOL_BODY_AMBIGUOUS);
    }

    @Test
    void theBodyRootIsAnObjectWithProperties() {
        assertThat(codeOf(() -> ToolValidator.validateBody(List.of(), Map.of("type", "array", "items", Map.of()))))
                .isEqualTo(ErrorCode.TOOL_BODY_SCHEMA_ROOT_INVALID);
    }

    @Test
    void aFieldComesFromAKnownSourceOnly() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
                "phone", Map.of("type", "string", "x-source", "header")));
        assertThat(codeOf(() -> ToolValidator.validateBody(List.of(), schema)))
                .isEqualTo(ErrorCode.TOOL_BODY_SOURCE_INVALID);
    }

    /** The model fills every array item; a configured value would have no item to go in. */
    @Test
    void aConfiguredFieldCannotSitInsideArrayItems() {
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
                "items", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                        "warehouse", Map.of("type", "string", "x-source", "static", "x-value", "W1"))))));
        assertThat(codeOf(() -> ToolValidator.validateBody(List.of(), schema)))
                .isEqualTo(ErrorCode.TOOL_BODY_SOURCE_IN_ARRAY);
    }

    @Test
    void noSchemaLeavesTheFlatBodyAlone() {
        ToolParamConfig flat = new ToolParamConfig("phone", "string", null, null, null, null, true);
        assertThat(codeOf(() -> ToolValidator.validateBody(List.of(flat), Map.of()))).isNull();
    }
}
