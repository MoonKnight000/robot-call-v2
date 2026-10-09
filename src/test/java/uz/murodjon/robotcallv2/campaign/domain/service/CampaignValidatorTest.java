package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

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
}
