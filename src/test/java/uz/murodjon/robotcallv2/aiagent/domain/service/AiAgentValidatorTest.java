package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentPhrases;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallAction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PostCallCondition;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallActionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PostCallConditionOperator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAgentValidatorTest {

    @Test
    void acceptsPhoneAndEmailExtractionTypes() {
        var agent = agentWith(
                new DataExtractionField("callback_number", "phone", "Callback number", "Caller number"),
                new DataExtractionField("email", "email", "Email", "Caller email"));

        assertThatCode(() -> AiAgentValidator.validate(agent, null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsFormatExamplesRequiredAndContactField() {
        var agent = agentWith(new DataExtractionField("callback_number", "phone", "Callback number", "Caller number",
                List.of(), List.of("+998 90 123 45 67"), true, "callback_phone"));

        assertThatCode(() -> AiAgentValidator.validate(agent, null)).doesNotThrowAnyException();
    }

    @Test
    void refusesTooManyFormatExamples() {
        var agent = agentWith(new DataExtractionField("city", "string", "City", "",
                List.of(), List.of("a", "b", "c", "d", "e", "f"), false, null));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_DATA_FIELD_FORMAT_EXAMPLES_INVALID));
    }

    @Test
    void refusesContactFieldThatIsNotAKey() {
        var agent = agentWith(new DataExtractionField("city", "string", "City", "",
                List.of(), List.of(), false, "home city"));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_DATA_FIELD_CONTACT_FIELD_INVALID));
    }

    @Test
    void acceptsConditionsSmsRecipientAndTelegramTarget() {
        var agent = agentWithActions(
                action(PostCallActionType.SEND_SMS, List.of(
                        new PostCallCondition("budget", PostCallConditionOperator.GREATER_THAN, "1000"),
                        new PostCallCondition("email", PostCallConditionOperator.IS_SET, null)),
                        "{{callback_phone}}", null, null),
                action(PostCallActionType.TELEGRAM, List.of(), null, 7L, "-1001234567890"));

        assertThatCode(() -> AiAgentValidator.validate(agent, null)).doesNotThrowAnyException();
    }

    @Test
    void refusesNumericConditionWithTextValue() {
        var agent = agentWithActions(action(PostCallActionType.SEND_SMS,
                List.of(new PostCallCondition("budget", PostCallConditionOperator.LESS_THAN, "ko'p")), null, null, null));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_POST_CALL_CONDITION_INVALID));
    }

    @Test
    void refusesSmsRecipientThatIsNeitherPhoneNorPlaceholder() {
        var agent = agentWithActions(action(PostCallActionType.SEND_SMS, List.of(), "menejer", null, null));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_POST_CALL_SMS_RECIPIENT_INVALID));
    }

    @Test
    void refusesTelegramChatWithoutBot() {
        var agent = agentWithActions(action(PostCallActionType.TELEGRAM, List.of(), null, null, "-100123"));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_POST_CALL_TELEGRAM_TARGET_INVALID));
    }

    @Test
    void refusesCodeActionWithoutCode() {
        var agent = agentWithActions(action(PostCallActionType.CODE, List.of(), null, null, null));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_POST_CALL_CODE_INVALID));
    }

    private static PostCallAction action(PostCallActionType type, List<PostCallCondition> conditions,
                                         String smsRecipient, Long telegramBotId, String telegramChatId) {
        return new PostCallAction("a-" + type, "Action", type, null, false, "Matn", null, Map.of(), true,
                conditions, smsRecipient, telegramBotId, telegramChatId, null);
    }

    private static AiAgent agentWithActions(PostCallAction... actions) {
        return AiAgentFixtures.agent(1, 1, 1, "uz-UZ", "test").toBuilder().postCallActions(List.of(actions)).build();
    }

    @Test
    void acceptsShortOwnPhrases() {
        var agent = agentWithPhrases(new AiAgentPhrases("Alo, Murod aka, eshityapsizmi?", null, null,
                "Rahmat, xayr!", null, null, List.of("Bir lahza."), List.of("Aha.", "Ha-ha.")));

        assertThatCode(() -> AiAgentValidator.validate(agent, null)).doesNotThrowAnyException();
    }

    @Test
    void refusesABackchannelThatIsASentence() {
        var agent = agentWithPhrases(new AiAgentPhrases(null, null, null, null, null, null,
                List.of(), List.of("Ha, men sizni diqqat bilan eshityapman")));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_BACKCHANNEL_TOO_LONG));
    }

    @Test
    void refusesAnOverlongPhrase() {
        var agent = agentWithPhrases(new AiAgentPhrases(null, null, null, "x".repeat(301), null, null,
                null, null));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_PHRASE_TOO_LONG));
    }

    @Test
    void refusesAnOversizedThinkingPool() {
        var agent = agentWithPhrases(new AiAgentPhrases(null, null, null, null, null, null,
                java.util.Collections.nCopies(11, "Bir lahza."), null));

        assertThatThrownBy(() -> AiAgentValidator.validate(agent, null))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.code()).isEqualTo(ErrorCode.AI_AGENT_PHRASE_POOL_INVALID));
    }

    private static AiAgent agentWithPhrases(AiAgentPhrases phrases) {
        return AiAgentFixtures.agent(1, 1, 1, "uz-UZ", "test").toBuilder().phrases(phrases).build();
    }

    private static AiAgent agentWith(DataExtractionField... fields) {
        return AiAgentFixtures.agent(1, 1, 1, "uz-UZ", "test").toBuilder().dataNeeded(List.of(fields)).build();
    }
}
