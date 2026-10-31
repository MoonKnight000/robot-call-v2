package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.ConversationStepConfig;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeContactUpdate;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeRoute;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.entity.StepOutcomes;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeExit;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequenceValidatorTest {

    private final SequenceValidator validator = new SequenceValidator();

    private static OutcomeRoute toStep(String stepId) {
        return new OutcomeRoute(OutcomeExit.STEP, stepId, 0);
    }

    private static SequenceStep conversation(String id, StepOutcomes outcomes) {
        return new SequenceStep(id, SequenceStepType.CONVERSATION, id, new ConversationStepConfig(null, ConversationStepConfig.DEFAULT_ATTEMPTS),
                null, null, outcomes, null, null);
    }

    private static SequenceStep conversationWithNoResponse(String id, String noResponseStepId) {
        return conversation(id, new StepOutcomes(List.of(), null, toStep(noResponseStepId)));
    }

    private static SequenceStep sms(String id, String next) {
        return new SequenceStep(id, SequenceStepType.SMS, id, null, "Salom", null, null, next, null);
    }

    @Test
    void theDefaultSequenceIsValid() {
        assertThatCode(() -> validator.validate(CampaignSequence.createDefault())).doesNotThrowAnyException();
    }

    @Test
    void aDraftConversationWithNoAgentYetIsAccepted() {
        CampaignSequence sequence = new CampaignSequence("c1",
                List.of(conversationWithNoResponse("c1", "s1"), sms("s1", null)), null);

        assertThatCode(() -> validator.validate(sequence)).doesNotThrowAnyException();
    }

    @Test
    void aNullSequenceHasNothingToValidate() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }

    @Test
    void aRouteToAMissingStepIsRejected() {
        CampaignSequence sequence = new CampaignSequence("c1", List.of(conversationWithNoResponse("c1", "nowhere")), null);

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).errors()).anyMatch(m -> m.contains("nowhere")));
    }

    @Test
    void aRouteLeadingBackIsRejected() {
        CampaignSequence sequence = new CampaignSequence("c1", List.of(
                conversationWithNoResponse("c1", "s1"), sms("s1", "c1")), null);

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aDoNotCallOutcomeThatLeadsToAStepIsRejected() {
        SequenceOutcome doNotCall = new SequenceOutcome("dnc", "Do Not Call", OutcomeEvaluation.PROMPT, "stop",
                null, null, new OutcomeContactUpdate(true, List.of()), toStep("s1"));
        CampaignSequence sequence = new CampaignSequence("c1", List.of(
                conversation("c1", new StepOutcomes(List.of(doNotCall), null, null)), sms("s1", null)), null);

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aGlobalOutcomeMayNotDelayAndIdsMustBeUnique() {
        SequenceOutcome delayed = new SequenceOutcome("a", "A", OutcomeEvaluation.PROMPT, "x", null, null, null,
                new OutcomeRoute(OutcomeExit.STEP, "c1", 60));
        SequenceOutcome duplicate = SequenceOutcome.ofPrompt("a", "A again", "y", OutcomeRoute.END);
        CampaignSequence sequence = new CampaignSequence("c1", List.of(conversation("c1", null)),
                List.of(delayed, duplicate));

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).errors())
                        .anyMatch(m -> m.contains("delayMinutes"))
                        .anyMatch(m -> m.contains("used by another outcome")));
    }

    @Test
    void aPromptOutcomeNeedsItsPrompt() {
        CampaignSequence sequence = new CampaignSequence("c1", List.of(conversation("c1", null)),
                List.of(SequenceOutcome.ofPrompt("a", "A", " ", OutcomeRoute.END)));

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }

    @Test
    void aConversationNeedsAtLeastOneAttempt() {
        SequenceStep noAttempts = new SequenceStep("c1", SequenceStepType.CONVERSATION, "c1",
                new ConversationStepConfig(null, List.of()), null, null, null, null, null);

        assertThatThrownBy(() -> validator.validate(new CampaignSequence("c1", List.of(noAttempts), null)))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).errors()).anyMatch(m -> m.contains("attempts")));
    }

    @Test
    void theSequenceMustStartOnAConversation() {
        CampaignSequence sequence = new CampaignSequence("s1", List.of(sms("s1", null)), null);

        assertThatThrownBy(() -> validator.validate(sequence)).isInstanceOf(ValidationException.class);
    }
}
