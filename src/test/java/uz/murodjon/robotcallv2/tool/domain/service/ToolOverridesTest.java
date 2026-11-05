package uz.murodjon.robotcallv2.tool.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolHttpConfig;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInput;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInputOverride;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolOverride;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolParamConfig;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolInputSource;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A tool's inputs as one list, and the tool as an agent's override changes it. */
class ToolOverridesTest {

    private static Map<String, Object> field(Object... pairs) {
        Map<String, Object> field = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            field.put((String) pairs[i], pairs[i + 1]);
        }
        return field;
    }

    /** The shape of the Uysot flat search: model-filled, static, variable and secret body fields, and a query param. */
    private static Tool searchFlats() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("rooms", field("type", "array", "description", "rooms the client wants"));
        properties.put("statuses", field("type", "array", Tool.BODY_SOURCE_KEY, "static", Tool.BODY_VALUE_KEY, List.of("SALE")));
        properties.put("phone", field("type", "string", Tool.BODY_SOURCE_KEY, "variable", Tool.BODY_VALUE_KEY, "phone_last9"));
        properties.put("token", field("type", "string", Tool.BODY_SOURCE_KEY, "secret", Tool.BODY_VALUE_KEY, "UYSOT"));
        ToolParamConfig page = new ToolParamConfig("page", "integer", ToolParamValueType.STATIC_VALUE, 1, null, null, false);
        return new Tool(null, null, "searchFlatsLive", "Searches flats.",
                new ToolHttpConfig("https://uysot.example/v1/open-api/flat/filter", "POST", null, null, List.of(page),
                        null, field("type", "object", "properties", properties), null, 0, null, null, null),
                null, null, Map.of("flats", "$.data[*]"), null, 6, null, null, null, null, null);
    }

    @Test
    void listsEveryInputButASecretOneWithItsSource() {
        List<ToolInput> inputs = ToolOverrides.findInputs(searchFlats());

        assertThat(inputs).extracting(ToolInput::name).containsExactly("page", "rooms", "statuses", "phone");
        assertThat(inputs.get(0).source()).isEqualTo(ToolInputSource.VALUE);
        assertThat(inputs.get(0).value()).isEqualTo(1);
        assertThat(inputs.get(1).source()).isEqualTo(ToolInputSource.AI);
        assertThat(inputs.get(1).description()).isEqualTo("rooms the client wants");
        assertThat(inputs.get(2).value()).isEqualTo(List.of("SALE"));
        assertThat(inputs.get(3).source()).isEqualTo(ToolInputSource.VARIABLE);
        assertThat(inputs.get(3).value()).isEqualTo("phone_last9");
    }

    @Test
    void waitForResultCanBeTurnedOffForOneUserAndTheRestStays() {
        Tool tool = searchFlats();

        Tool detached = ToolOverrides.withWaitForResult(tool, false);

        assertThat(tool.http().waitForResult()).isTrue();
        assertThat(detached.http().waitForResult()).isFalse();
        assertThat(detached.http().url()).isEqualTo(tool.http().url());
        assertThat(detached.http().bodySchema()).isEqualTo(tool.http().bodySchema());
        assertThat(detached.responseMapping()).isEqualTo(tool.responseMapping());
        assertThat(detached.maxCallsPerCall()).isEqualTo(tool.maxCallsPerCall());
        assertThat(ToolOverrides.withWaitForResult(tool, true)).isSameAs(tool);
    }

    @Test
    void aFixedValueTakesABodyFieldAwayFromTheModel() {
        Tool tool = ToolOverrides.apply(searchFlats(), new ToolOverride(
                List.of(new ToolInputOverride("rooms", ToolInputSource.VALUE, List.of("3"), null)), null));

        Map<?, ?> rooms = (Map<?, ?>) ((Map<?, ?>) tool.http().bodySchema().get("properties")).get("rooms");
        assertThat(rooms.get(Tool.BODY_SOURCE_KEY)).isEqualTo("static");
        assertThat(rooms.get(Tool.BODY_VALUE_KEY)).isEqualTo(List.of("3"));
        assertThat(rooms.get("type")).isEqualTo("array");
    }

    @Test
    void aBodyFieldCanBeHandedBackToTheModelWithANewDescription() {
        Tool tool = ToolOverrides.apply(searchFlats(), new ToolOverride(
                List.of(new ToolInputOverride("statuses", ToolInputSource.AI, null, "which statuses to look for")), null));

        Map<?, ?> statuses = (Map<?, ?>) ((Map<?, ?>) tool.http().bodySchema().get("properties")).get("statuses");
        assertThat(statuses.containsKey(Tool.BODY_SOURCE_KEY)).isFalse();
        assertThat(statuses.containsKey(Tool.BODY_VALUE_KEY)).isFalse();
        assertThat(statuses.get("description")).isEqualTo("which statuses to look for");
    }

    @Test
    void aParameterTakesTheVariableItIsPointedAt() {
        Tool tool = ToolOverrides.apply(searchFlats(), new ToolOverride(
                List.of(new ToolInputOverride("page", ToolInputSource.VARIABLE, "page_number", null)), null));

        ToolParamConfig page = tool.http().queryParams().getFirst();
        assertThat(page.valueType()).isEqualTo(ToolParamValueType.DYNAMIC_VARIABLE);
        assertThat(page.value()).isEqualTo("page_number");
    }

    @Test
    void aSecretFieldAndAnUnknownInputAreLeftAlone() {
        Tool original = searchFlats();
        Tool tool = ToolOverrides.apply(original, new ToolOverride(List.of(
                new ToolInputOverride("token", ToolInputSource.VALUE, "stolen", null),
                new ToolInputOverride("nonexistent", ToolInputSource.VALUE, "x", null)), null));

        Map<?, ?> token = (Map<?, ?>) ((Map<?, ?>) tool.http().bodySchema().get("properties")).get("token");
        assertThat(token.get(Tool.BODY_SOURCE_KEY)).isEqualTo("secret");
        assertThat(token.get(Tool.BODY_VALUE_KEY)).isEqualTo("UYSOT");
    }

    @Test
    void theAnswerFieldsReplaceTheToolsOnlyWhenGiven() {
        Tool original = searchFlats();

        assertThat(ToolOverrides.apply(original, new ToolOverride(null, Map.of("names", "$.data[*].name")))
                .responseMapping()).containsOnlyKeys("names");
        assertThat(ToolOverrides.apply(original, new ToolOverride(
                List.of(new ToolInputOverride("rooms", ToolInputSource.AI, null, null)), Map.of())).responseMapping())
                .containsOnlyKeys("flats");
    }

    @Test
    void nothingToChangeIsTheSameTool() {
        Tool original = searchFlats();

        assertThat(ToolOverrides.apply(original, null)).isSameAs(original);
        assertThat(ToolOverrides.apply(original, new ToolOverride(null, null))).isSameAs(original);
    }

    @Test
    void theOriginalToolIsNotChanged() {
        Tool original = searchFlats();
        ToolOverrides.apply(original, new ToolOverride(
                List.of(new ToolInputOverride("rooms", ToolInputSource.VALUE, List.of("3"), null)), null));

        Map<?, ?> rooms = (Map<?, ?>) ((Map<?, ?>) original.http().bodySchema().get("properties")).get("rooms");
        assertThat(rooms.containsKey(Tool.BODY_SOURCE_KEY)).isFalse();
    }
}
