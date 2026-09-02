package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateEngineTest {

    @ParameterizedTest
    @NullAndEmptySource
    void returnsTemplateWhenEmptyOrNull(String template) {
        assertThat(PromptTemplateEngine.render(template, Map.of("name", "Ali"))).isEqualTo(template);
    }

    @Test
    void returnsTemplateWhenVariablesAreNullOrEmpty() {
        String template = "Assalomu alaykum {{name}}";
        assertThat(PromptTemplateEngine.render(template, null)).isEqualTo(template);
        assertThat(PromptTemplateEngine.render(template, Map.of())).isEqualTo(template);
    }

    @Test
    void replacesSimplePlaceholders() {
        String template = "Salom {{name}}, sizning qarzingiz {{amount}} so'm.";
        Map<String, Object> vars = Map.of("name", "Anvar", "amount", "150000");

        String rendered = PromptTemplateEngine.render(template, vars);
        assertThat(rendered).isEqualTo("Salom Anvar, sizning qarzingiz 150000 so'm.");
    }

    @Test
    void replacesCaseInsensitivePlaceholders() {
        String template = "Hurmatli {{CLIENT_NAME}}, balansingiz: {{Balance}}";
        Map<String, Object> vars = Map.of("client_name", "Jamshid", "balance", 50000);

        String rendered = PromptTemplateEngine.render(template, vars);
        assertThat(rendered).isEqualTo("Hurmatli Jamshid, balansingiz: 50000");
    }

    @Test
    void usesFallbackWhenVariableMissing() {
        String template = "Salom {{clientName | Hurmatli mijoz}}, sizda {{amount | 0}} so'm to'lov bor.";
        Map<String, Object> vars = Map.of("otherKey", "value");

        String rendered = PromptTemplateEngine.render(template, vars);
        assertThat(rendered).isEqualTo("Salom Hurmatli mijoz, sizda 0 so'm to'lov bor.");
    }

    @Test
    void usesQuotedFallbackWhenVariableMissing() {
        String template = "Assalomu alaykum, {{name | \"Hurmatli foydalanuvchi\"}}!";
        Map<String, Object> vars = Map.of("otherKey", "value");

        String rendered = PromptTemplateEngine.render(template, vars);
        assertThat(rendered).isEqualTo("Assalomu alaykum, Hurmatli foydalanuvchi!");
    }

    @Test
    void usesValueInsteadOfFallbackWhenValuePresent() {
        String template = "Salom {{name | \"Hurmatli mijoz\"}}";
        Map<String, Object> vars = Map.of("name", "Sardor");

        String rendered = PromptTemplateEngine.render(template, vars);
        assertThat(rendered).isEqualTo("Salom Sardor");
    }

    @Test
    void handlesBlankValueByFallingBack() {
        String template = "Salom {{name | \"Hurmatli mijoz\"}}";
        Map<String, Object> vars = Map.of("name", "   ");

        String rendered = PromptTemplateEngine.render(template, vars);
        assertThat(rendered).isEqualTo("Salom Hurmatli mijoz");
    }
}
