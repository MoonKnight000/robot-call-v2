package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryPolicy;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignValidatorTest {

    private final CampaignValidator validator = new CampaignValidator();

    @Test
    void aiCampaignNeedsAnAgent() {
        assertThatThrownBy(() -> validator.validateDialMode(DialMode.AI, null, null))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.CAMPAIGN_AI_AGENT_REQUIRED);
    }

    @Test
    void aiCampaignNeedsNoQueue() {
        assertThatCode(() -> validator.validateDialMode(DialMode.AI, 7L, null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = DialMode.class, names = "AI", mode = EnumSource.Mode.EXCLUDE)
    void operatorCampaignNeedsAQueue(DialMode dialMode) {
        assertThatThrownBy(() -> validator.validateDialMode(dialMode, 7L, null))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.CAMPAIGN_CALL_QUEUE_REQUIRED);
    }

    @ParameterizedTest
    @EnumSource(value = DialMode.class, names = {"PREVIEW", "PROGRESSIVE"})
    void operatorCampaignNeedsNoAgent(DialMode dialMode) {
        assertThatCode(() -> validator.validateDialMode(dialMode, null, 5L)).doesNotThrowAnyException();
    }

    @Test
    void predictiveCampaignNeedsTheOverflowAgent() {
        assertThatThrownBy(() -> validator.validateDialMode(DialMode.PREDICTIVE, null, 5L))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.CAMPAIGN_AI_AGENT_REQUIRED);
    }

    private static RetryPolicy smsAfter(Disposition disposition, String text, Integer onAttempt) {
        return new RetryPolicy(Map.of(disposition, new RetryRule(null, null, text, onAttempt)));
    }

    @Test
    void aNoAnswerFollowUpSmsIsAccepted() {
        assertThatCode(() -> validator.validateRetryPolicy(
                smsAfter(Disposition.NO_ANSWER, "Sizga qo'ng'iroq qildik, {{clientName}}.", 1)))
                .doesNotThrowAnyException();
    }

    /** A stranger would read the client's facts; a "do not contact" asked not to be. */
    @ParameterizedTest
    @EnumSource(value = Disposition.class, names = {"WRONG_NUMBER", "DO_NOT_CALL"})
    void noSmsGoesToAStrangerOrADoNotContact(Disposition disposition) {
        assertThatThrownBy(() -> validator.validateRetryPolicy(smsAfter(disposition, "Salom", null)))
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.CAMPAIGN_FOLLOW_UP_SMS_NOT_ALLOWED);
    }

    @Test
    void aFollowUpSmsLongerThanThreePartsIsRefused() {
        String text = "a".repeat(CampaignValidator.MAX_FOLLOW_UP_SMS_LENGTH + 1);
        assertThatThrownBy(() -> validator.validateRetryPolicy(smsAfter(Disposition.NO_ANSWER, text, null)))
                .extracting(e -> ((AppException) e).code())
                .isEqualTo(ErrorCode.CAMPAIGN_FOLLOW_UP_SMS_TOO_LONG);
    }

    @Test
    void aRuleWithoutSmsIsNotChecked() {
        assertThatCode(() -> validator.validateRetryPolicy(new RetryPolicy(Map.of(
                Disposition.WRONG_NUMBER, new RetryRule(1, null, null, null)))))
                .doesNotThrowAnyException();
    }
}
