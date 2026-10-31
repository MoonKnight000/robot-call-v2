package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequenceValidatorTest {

    private final SequenceValidator validator = new SequenceValidator();

    private static SequenceStep conversation(String id, Map<SequenceOutcome, String> routes) {
        return new SequenceStep(id, SequenceStepType.CONVERSATION, id, new ConversationStepConfig(null, 3, 0),
                null, null, routes, null, null);
    }

    private static SequenceStep sms(String id, String next) {
        return new SequenceStep(id, SequenceStepType.SMS, id, null, "Salom", null, Map.of(), next, null);
    }

    @Test
    void aDraftConversationWithNoAgentYetIsAccepted() {
        CampaignSequence sequence = new CampaignSequence("c1",
                List.of(conversation("c1", Map.of(SequenceOutcome.NO_RESPONSE, "s1")), sms("s1", null)));

        assertThatCode(() -> validator.validate(sequence)).doesNotThrowAnyException();
    }

    @Test
    void aNullSequenceHasNothingToValidate() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }

    @Test
    void aRouteToAMissingStepIsRejected() {
        CampaignSequence sequence = new CampaignSequence("c1",
                List.of(conversation("c1", Map.of(SequenceOutcome.NO_RESPONSE, "nowhere"))));

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).errors()).anyMatch(m -> m.contains("nowhere")));
    }

    @Test
    void aRouteLeadingBackIsRejected() {
        CampaignSequence sequence = new CampaignSequence("c1", List.of(
                conversation("c1", Map.of(SequenceOutcome.NO_RESPONSE, "s1")), sms("s1", "c1")));

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aDoNotCallRouteIsRejected() {
        CampaignSequence sequence = new CampaignSequence("c1", List.of(
                conversation("c1", Map.of(SequenceOutcome.DO_NOT_CALL, "s1")), sms("s1", null)));

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }

    @Test
    void theSequenceMustStartOnAConversation() {
        CampaignSequence sequence = new CampaignSequence("s1", List.of(sms("s1", null)));

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }
}
