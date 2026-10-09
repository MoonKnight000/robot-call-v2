package uz.murodjon.robotcallv2.aiagent.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

public final class AgentRolloutValidator {

    public static final int MAX_STAGING_PHONES = 20;

    private AgentRolloutValidator() {
    }

    /**
     * @param stagingPhones already normalised; a null entry is a number that could not be read
     */
    public static void validateSplit(int trafficPercent, List<String> stagingPhones) {
        List<String> errors = new ArrayList<>();
        if (trafficPercent < 0 || trafficPercent > 100) {
            errors.add("trafficPercent: 0–100");
        }
        if (stagingPhones.size() > MAX_STAGING_PHONES) {
            errors.add("stagingPhones: at most " + MAX_STAGING_PHONES);
        }
        if (stagingPhones.stream().anyMatch(phone -> phone == null || phone.isBlank())) {
            errors.add("stagingPhones: every entry must be a phone number");
        }
        if (trafficPercent == 0 && stagingPhones.isEmpty()) {
            errors.add("trafficPercent: 0 with no stagingPhones sends nobody to the candidate");
        }
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.AGENT_ROLLOUT_INVALID, errors, errors.size());
        }
    }
}
