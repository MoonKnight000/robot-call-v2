package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionExtract;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSms;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionTransfer;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionVariable;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsContentMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsRecipient;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVariableType;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolInputOverride;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolOverride;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolInputSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentFunctionValidatorTest {

    @Test
    void systemFunctionsTakeTheirFixedNamesWhateverTheRequestSays() {
        List<AgentFunction> normalized = AgentFunctionValidator.normalize(List.of(
                AgentFunction.builder().id("a").type(AgentFunctionType.END_CALL).name("hangUp").build(),
                AgentFunction.builder().id("b").type(AgentFunctionType.TRANSFER_CALL).name("x")
                        .transfer(new AgentFunctionTransfer("+998901234567", null)).build()));

        assertThat(normalized).extracting(AgentFunction::name).containsExactly("endCall", "requestHumanTransfer");
    }

    @Test
    void dropsTheSettingsOfOtherTypes() {
        AgentFunction function = AgentFunction.builder().id("a").type(AgentFunctionType.END_CALL)
                .transfer(new AgentFunctionTransfer("+998901234567", null)).toolId(5L).build();

        AgentFunction normalized = AgentFunctionValidator.normalize(List.of(function)).getFirst();

        assertThat(normalized.transfer()).isNull();
        assertThat(normalized.toolId()).isNull();
    }

    @Test
    void refusesASystemTypeTwice() {
        List<AgentFunction> functions = AgentFunctionValidator.normalize(List.of(
                AgentFunction.builder().id("a").type(AgentFunctionType.END_CALL).build(),
                AgentFunction.builder().id("b").type(AgentFunctionType.END_CALL).build()));

        assertRefused(functions, ErrorCode.AI_AGENT_FUNCTION_DUPLICATE);
    }

    @Test
    void refusesARepeatedId() {
        List<AgentFunction> functions = AgentFunctionValidator.normalize(List.of(
                AgentFunction.builder().id("a").type(AgentFunctionType.END_CALL).build(),
                AgentFunction.builder().id("a").type(AgentFunctionType.PRESS_DIGIT).build()));

        assertRefused(functions, ErrorCode.AI_AGENT_FUNCTION_DUPLICATE);
    }

    @Test
    void refusesAnExtractionNamedLikeASystemTool() {
        assertRefused(extraction("endCall", variable("budget", AgentVariableType.NUMBER)),
                ErrorCode.AI_AGENT_FUNCTION_INVALID);
    }

    @Test
    void refusesAnEnumVariableWithoutChoices() {
        assertRefused(extraction("pickPlan", variable("plan", AgentVariableType.ENUM)),
                ErrorCode.AI_AGENT_FUNCTION_INVALID);
    }

    @Test
    void acceptsAnExtractionWithAnEnumVariable() {
        List<AgentFunction> functions = extraction("pickPlan",
                new AgentFunctionVariable("plan", AgentVariableType.ENUM, "Chosen plan", List.of("basic", "pro"), false));

        assertThatCode(() -> AgentFunctionValidator.validate(functions)).doesNotThrowAnyException();
    }

    @Test
    void refusesATransferDestinationThatIsNotADestination() {
        List<AgentFunction> functions = AgentFunctionValidator.normalize(List.of(AgentFunction.builder().id("a")
                .type(AgentFunctionType.TRANSFER_CALL).transfer(new AgentFunctionTransfer("call me maybe", null)).build()));

        assertRefused(functions, ErrorCode.AI_AGENT_FUNCTION_INVALID);
    }

    @Test
    void acceptsAVariableAsATransferDestination() {
        List<AgentFunction> functions = AgentFunctionValidator.normalize(List.of(AgentFunction.builder().id("a")
                .type(AgentFunctionType.TRANSFER_CALL).transfer(new AgentFunctionTransfer("{{manager_phone}}", null)).build()));

        assertThatCode(() -> AgentFunctionValidator.validate(functions)).doesNotThrowAnyException();
    }

    @Test
    void anSmsToANumberNeedsTheNumber() {
        List<AgentFunction> functions = AgentFunctionValidator.normalize(List.of(AgentFunction.builder().id("a")
                .type(AgentFunctionType.SEND_SMS)
                .sms(new AgentFunctionSms(AgentSmsContentMode.STATIC, "Hello", AgentSmsRecipient.NUMBER, null, true))
                .build()));

        assertRefused(functions, ErrorCode.AI_AGENT_FUNCTION_INVALID);
    }

    @Test
    void anAbsentListIsNotValidated() {
        assertThatCode(() -> AgentFunctionValidator.validate(null)).doesNotThrowAnyException();
    }

    private static List<AgentFunction> extraction(String name, AgentFunctionVariable... variables) {
        return AgentFunctionValidator.normalize(List.of(AgentFunction.builder().id("a")
                .type(AgentFunctionType.EXTRACT_VARIABLE).name(name)
                .extract(new AgentFunctionExtract(List.of(variables))).build()));
    }

    private static AgentFunctionVariable variable(String name, AgentVariableType type) {
        return new AgentFunctionVariable(name, type, null, List.of(), false);
    }

    @Test
    void keepsTheIntegrationOverrideOnlyOnAnIntegrationFunction() {
        ToolOverride override = new ToolOverride(List.of(new ToolInputOverride("rooms", ToolInputSource.VALUE, "3", null)), null);
        List<AgentFunction> normalized = AgentFunctionValidator.normalize(List.of(
                AgentFunction.builder().id("a").type(AgentFunctionType.INTEGRATION).integrationTool("searchFlatsLive")
                        .integration(override).build(),
                AgentFunction.builder().id("b").type(AgentFunctionType.END_CALL).integration(override).build()));

        assertThat(normalized.get(0).integration()).isEqualTo(override);
        assertThat(normalized.get(1).integration()).isNull();
        assertThatCode(() -> AgentFunctionValidator.validate(normalized)).doesNotThrowAnyException();
    }

    @Test
    void refusesAnIntegrationInputWithoutItsValue() {
        assertRefused(integration(new ToolOverride(List.of(new ToolInputOverride("rooms", ToolInputSource.VALUE, null, null)), null)),
                ErrorCode.AI_AGENT_FUNCTION_INVALID);
        assertRefused(integration(new ToolOverride(List.of(new ToolInputOverride("phone", ToolInputSource.VARIABLE, "not a name", null)), null)),
                ErrorCode.AI_AGENT_FUNCTION_INVALID);
        assertRefused(integration(new ToolOverride(List.of(new ToolInputOverride("rooms", null, "3", null)), null)),
                ErrorCode.AI_AGENT_FUNCTION_INVALID);
    }

    @Test
    void refusesAnIntegrationInputNamedTwice() {
        assertRefused(integration(new ToolOverride(List.of(
                        new ToolInputOverride("rooms", ToolInputSource.AI, null, null),
                        new ToolInputOverride("rooms", ToolInputSource.VALUE, "2", null)), null)),
                ErrorCode.AI_AGENT_FUNCTION_INVALID);
    }

    @Test
    void refusesAnAnswerFieldThatIsNotAPath() {
        assertRefused(integration(new ToolOverride(null, Map.of("flats", "data[*]"))), ErrorCode.AI_AGENT_FUNCTION_INVALID);
        assertRefused(integration(new ToolOverride(null, Map.of("flats", "$.data[*] || data"))), ErrorCode.AI_AGENT_FUNCTION_INVALID);
        assertRefused(integration(new ToolOverride(null, Map.of("1flats", "$.data[*]"))), ErrorCode.AI_AGENT_FUNCTION_INVALID);
        assertThatCode(() -> AgentFunctionValidator.validate(integration(new ToolOverride(null, Map.of("flats", "$.data[*] || $.data.content[*]")))))
                .doesNotThrowAnyException();
    }

    private static List<AgentFunction> integration(ToolOverride override) {
        return AgentFunctionValidator.normalize(List.of(AgentFunction.builder().id("a").type(AgentFunctionType.INTEGRATION)
                .integrationTool("searchFlatsLive").integration(override).build()));
    }

    private static void assertRefused(List<AgentFunction> functions, ErrorCode code) {
        assertThatThrownBy(() -> AgentFunctionValidator.validate(functions))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
