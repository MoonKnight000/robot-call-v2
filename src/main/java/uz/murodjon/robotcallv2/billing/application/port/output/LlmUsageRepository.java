package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.LlmUsageLine;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelSpend;

import java.time.Instant;
import java.util.List;

/** Per-model LLM charge lines (llm_usage). Append-only, like the ledger they explain. */
public interface LlmUsageRepository {

    /** Writes the lines; ids and {@code createdAt} are storage's. */
    void saveAll(List<LlmUsageLine> lines);

    /** The company's lines written in {@code [from, to)}, summed per model and purpose. */
    List<ModelSpend> findModelSpend(long companyId, Instant from, Instant to);
}
