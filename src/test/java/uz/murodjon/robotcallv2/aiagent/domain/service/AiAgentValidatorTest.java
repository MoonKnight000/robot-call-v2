package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.DataExtractionField;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class AiAgentValidatorTest {

    @Test
    void acceptsPhoneAndEmailExtractionTypes() {
        var agent = AiAgentFixtures.agent(1, 1, 1, "uz-UZ", "test")
                .toBuilder()
                .dataNeeded(List.of(
                        new DataExtractionField("callback_number", "phone", "Callback number", "Caller number"),
                        new DataExtractionField("email", "email", "Email", "Caller email")))
                .build();

        assertThatCode(() -> AiAgentValidator.validate(agent, null)).doesNotThrowAnyException();
    }
}
