package uz.murodjon.robotcallv2.pack.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.pack.domain.entity.PackSecret;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioValidator;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;
import uz.murodjon.robotcallv2.tool.domain.service.ToolValidator;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The packs as shipped: an install creates their parts through the same use cases a person
 * would, so what those would refuse — or accept and then fail on during a call — is caught here.
 */
class ClasspathJsonPackCatalogAdapterTest {

    private static final Pattern SECRET_REFERENCE = Pattern.compile("\\{\\{secrets\\.([A-Za-z0-9_.-]+)}}");

    private final ClasspathJsonPackCatalogAdapter catalog = new ClasspathJsonPackCatalogAdapter(new ObjectMapper());

    @Test
    void theOnlineShopPackShips() {
        assertThat(catalog.findByKey("ecommerce")).isNotNull();
        assertThat(catalog.findByKey("no-such-pack")).isNull();
    }

    @Test
    void everyScenarioPassesTheScenarioValidator() {
        for (VerticalPack pack : catalog.findAll()) {
            assertThat(ScenarioValidator.validate(pack.agent().scenario())).as(pack.key()).isEmpty();
        }
    }

    /**
     * A pack's goal must be recorded either by one of its company tools' mapped answers
     * or by an internal scenario tool's parameter (e.g. nps).
     */
    @Test
    void everyGoalIsRecordedByOneOfThePacksTools() {
        for (VerticalPack pack : catalog.findAll()) {
            String goal = pack.agent().scenario().findGoalField();
            assertThat(goal).as(pack.key()).isNotNull();
            boolean recordedByScenarioTool = pack.agent().scenario().tools() != null
                    && pack.agent().scenario().tools().stream()
                            .anyMatch(t -> t.params() != null && t.params().stream().anyMatch(p -> goal.equals(p.name())));
            if (!recordedByScenarioTool) {
                assertThat(pack.tools()).as(pack.key())
                        .anySatisfy(tool -> assertThat(tool.responseMapping()).containsKey(goal));
            }
        }
    }

    @Test
    void everyToolIsOneTheToolEndpointAccepts() {
        for (VerticalPack pack : catalog.findAll()) {
            for (Tool tool : pack.tools()) {
                assertThatCode(() -> ToolValidator.validateBody(tool.apiBody(), tool.bodySchema()))
                        .as(tool.name()).doesNotThrowAnyException();
                assertThat(tool.effect()).as(tool.name()).isNotEqualTo(ToolEffect.DESTRUCTIVE);
                // 0 means "no limit" — a pack must never ship that by leaving the field out.
                assertThat(tool.maxCallsPerCall()).as(tool.name()).isPositive();
            }
        }
    }

    /** A write a stranger's voice triggers is read back before it is sent (UNIVERSAL-OPERATOR-PLAN B.3). */
    @Test
    void everyWritingToolAsksForConfirmation() {
        for (VerticalPack pack : catalog.findAll()) {
            for (Tool tool : pack.tools()) {
                if (tool.effect() == ToolEffect.WRITE) {
                    assertThat(tool.requiresConfirmation()).as(tool.name()).isTrue();
                }
            }
        }
    }

    /** The install answer lists the missing secrets from the pack's own list, so it has to name every one the tools read. */
    @Test
    void everySecretTheToolsReadIsDeclared() {
        for (VerticalPack pack : catalog.findAll()) {
            Set<String> referenced = new HashSet<>();
            for (Tool tool : pack.tools()) {
                collectSecretReferences(tool.apiUrl(), referenced);
                for (ToolKeyValuePair header : tool.apiHeaders()) {
                    collectSecretReferences(header.value(), referenced);
                }
            }
            Set<String> declared = new HashSet<>();
            pack.secrets().stream().map(PackSecret::key).forEach(declared::add);
            assertThat(declared).as(pack.key()).containsAll(referenced);
        }
    }

    /** UNIVERSAL-OPERATOR-PLAN C.2: at least five personas before a pack counts as ready. */
    @Test
    void everyPackHasAtLeastFiveTestCases() {
        for (VerticalPack pack : catalog.findAll()) {
            assertThat(pack.testCases()).as(pack.key()).hasSizeGreaterThanOrEqualTo(5);
        }
    }

    private static void collectSecretReferences(String text, Set<String> into) {
        if (text == null) {
            return;
        }
        Matcher matcher = SECRET_REFERENCE.matcher(text);
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }
}
