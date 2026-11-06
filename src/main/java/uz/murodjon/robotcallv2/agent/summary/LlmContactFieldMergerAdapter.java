package uz.murodjon.robotcallv2.agent.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.TokenUsage;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.contact.application.port.output.ContactFieldMergerPort;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.MergedFieldValue;

import java.util.Map;

/**
 * Retell's "Accumulate & summarize": the stored and the new value of one contact field go to
 * the model in one request, with the field's description as the merge instruction.
 */
@Component
public class LlmContactFieldMergerAdapter implements ContactFieldMergerPort {

    private static final Logger log = LoggerFactory.getLogger(LlmContactFieldMergerAdapter.class);

    private static final String SYSTEM = """
            You keep one field of a customer's profile up to date across phone calls.
            Field: %s (%s).
            What the field captures and how to merge it: %s
            Merge the STORED value and the NEW value from the latest call into one concise text,
            in the language the values are written in. Keep every fact from both unless the new
            value replaces it; drop repetitions. At most 900 characters.
            Answer only with JSON: {"value": "<merged text>"}
            """;

    private final SummaryService summaryService;
    private final AiModelConfigUseCase aiModelConfigUseCase;

    public LlmContactFieldMergerAdapter(SummaryService summaryService, AiModelConfigUseCase aiModelConfigUseCase) {
        this.summaryService = summaryService;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
    }

    @Override
    public MergedFieldValue generateMergedValue(long companyId, String model, ContactField field, String stored, String incoming) {
        String description = field.description() != null && !field.description().isBlank()
                ? field.description() : "no instruction; keep the latest facts and the history in short.";
        String system = SYSTEM.formatted(field.label(), field.key(), description);
        String user = "STORED:\n" + stored + "\n\nNEW:\n" + incoming;
        TokenUsage tokens = new TokenUsage();
        Map<String, Object> answer = summaryService.askAboutCall(companyId, model, system, user, tokens);
        Object value = answer != null ? answer.get("value") : null;
        if (value == null || value.toString().isBlank()) {
            log.debug("Contact field '{}' merge gave no value (company {})", field.key(), companyId);
            return null;
        }
        boolean ownLlm = aiModelConfigUseCase.findEffectiveByCompanyId(companyId).ownLlm() != null;
        return new MergedFieldValue(value.toString().strip(), tokens.model(), tokens.promptTokens(),
                tokens.completionTokens(), ownLlm);
    }
}
