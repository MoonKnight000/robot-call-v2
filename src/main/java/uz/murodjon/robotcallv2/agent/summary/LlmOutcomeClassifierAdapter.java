package uz.murodjon.robotcallv2.agent.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.port.output.OutcomeClassifierPort;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;

import java.util.List;
import java.util.Map;

/**
 * Retell's outcome classifier: the transcript and every PROMPT outcome go to the summary model in
 * one request, and it names the single outcome the call reached, or none.
 */
@Component
public class LlmOutcomeClassifierAdapter implements OutcomeClassifierPort {

    private static final Logger log = LoggerFactory.getLogger(LlmOutcomeClassifierAdapter.class);
    private static final String NONE = "none";

    private final SummaryService summaryService;

    public LlmOutcomeClassifierAdapter(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Override
    public String findMatchingOutcomeId(long companyId, String transcript, List<SequenceOutcome> outcomes) {
        if (outcomes.isEmpty() || transcript == null || transcript.isBlank()) {
            return null;
        }
        Map<String, Object> answer = summaryService.askAboutCall(companyId, systemPrompt(outcomes), transcript);
        Object id = answer != null ? answer.get("outcome") : null;
        if (id == null) {
            return null;
        }
        String chosen = String.valueOf(id).trim();
        boolean known = outcomes.stream().anyMatch(outcome -> outcome.id().equals(chosen));
        if (!known && !NONE.equalsIgnoreCase(chosen)) {
            log.warn("Outcome classifier answered an unknown outcome '{}' (company {})", chosen, companyId);
        }
        return known ? chosen : null;
    }

    private static String systemPrompt(List<SequenceOutcome> outcomes) {
        StringBuilder sb = new StringBuilder("""
                You classify the transcript of a finished phone call between an AI agent and a lead.
                Decide which ONE of the outcomes below the call reached, judging by meaning, not by exact words.
                The transcript may be in Uzbek or Russian. If no outcome clearly fits, answer "none".
                Answer only with JSON: {"outcome": "<outcome id or none>"}

                OUTCOMES:
                """);
        for (SequenceOutcome outcome : outcomes) {
            sb.append("- id: ").append(outcome.id())
                    .append(" | name: ").append(outcome.name())
                    .append(" | the call reached it when: ").append(outcome.prompt())
                    .append('\n');
        }
        return sb.toString();
    }
}
