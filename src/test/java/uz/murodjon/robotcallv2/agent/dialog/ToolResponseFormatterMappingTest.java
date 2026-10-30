package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a tool's {@code responseMapping} picks out of an answer, lists included. */
class ToolResponseFormatterMappingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToolResponseFormatter formatter = new ToolResponseFormatter(JSON);

    private static final String ANSWER = """
            {"data": {"items": [
              {"number": "12", "rooms": 2, "price": 450000000, "house": {"name": "Navruz"}, "owner": "secret"},
              {"number": "14", "rooms": 3, "price": 610000000, "house": {"name": "Navruz"}, "owner": "secret"}
            ], "total": 2}}""";

    private JsonNode map(String path) throws Exception {
        return formatter.applyMapping(JSON.readTree(ANSWER), Map.of("out", path)).path("out");
    }

    @Test
    void aPathWithoutAWildcardStillNamesOneValue() throws Exception {
        assertThat(map("$.data.items[0].price").asLong()).isEqualTo(450000000L);
        assertThat(map("$.data.total").asInt()).isEqualTo(2);
    }

    @Test
    void aWildcardAloneIsTheListAsItIs() throws Exception {
        assertThat(map("$.data.items[*]").size()).isEqualTo(2);
        assertThat(map("$.data.items[*]").get(0).has("owner")).isTrue();
    }

    @Test
    void aWildcardWithAPathIsEveryElementsValue() throws Exception {
        assertThat(map("$.data.items[*].price")).hasSize(2);
        assertThat(map("$.data.items[*].price").get(1).asLong()).isEqualTo(610000000L);
        assertThat(map("$.data.items[*].house.name").get(0).asText()).isEqualTo("Navruz");
    }

    @Test
    void aWildcardWithFieldsCutsEveryElementDownToThem() throws Exception {
        JsonNode flats = map("$.data.items[*]{number,rooms,house.name}");

        assertThat(flats).hasSize(2);
        assertThat(flats.get(0).get("number").asText()).isEqualTo("12");
        assertThat(flats.get(0).get("name").asText()).isEqualTo("Navruz");
        assertThat(flats.get(0).has("owner")).as("a field that was not asked for never reaches the model").isFalse();
        assertThat(flats.get(0).has("price")).isFalse();
    }

    @Test
    void aFieldTheElementLacksIsLeftOut() throws Exception {
        JsonNode flats = map("$.data.items[*]{number,floor}");

        assertThat(flats.get(0).has("number")).isTrue();
        assertThat(flats.get(0).has("floor")).isFalse();
    }

    @Test
    void aWildcardOverSomethingThatIsNotAListSelectsNothing() throws Exception {
        assertThat(map("$.data.total[*]").isMissingNode()).isTrue();
        assertThat(map("$.nowhere[*].price").isMissingNode()).isTrue();
    }

    @Test
    void theFirstAlternativeThatSelectsSomethingIsUsed() throws Exception {
        // The answer wraps its list as data.items: the first two shapes are not there.
        JsonNode picked = map("$.data[*]{number} || $.data.data[*]{number} || $.data.items[*]{number}");

        assertThat(picked.size()).isEqualTo(2);
        assertThat(picked.get(0).path("number").asText()).isEqualTo("12");
    }

    @Test
    void anEmptyListIsFoundAndStopsTheAlternatives() throws Exception {
        JsonNode answer = JSON.readTree("{\"data\": []}");

        JsonNode picked = formatter.applyMapping(answer,
                Map.of("out", "$.data[*]{number} || $.data.content[*]{number}")).path("out");

        assertThat(picked.isArray()).isTrue();
        assertThat(picked.size()).isZero();
    }

    @Test
    void whenNoAlternativeIsThereNothingIsSelected() throws Exception {
        assertThat(map("$.nope[*] || $.nada[*]").isMissingNode()).isTrue();
    }

    @Test
    void aSecondWildcardSelectsNothing() throws Exception {
        assertThat(map("$.data.items[*].x[*]").isMissingNode()).isTrue();
    }
}
