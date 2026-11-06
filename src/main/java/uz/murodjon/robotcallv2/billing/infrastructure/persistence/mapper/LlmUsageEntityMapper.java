package uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.domain.entity.LlmUsageLine;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelLlmCost;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.LlmUsageEntity;

import java.time.Instant;

/** Write-only for now: nothing reads a line back through the port yet. */
@Component
public class LlmUsageEntityMapper {

    public LlmUsageEntity toLlmUsageEntity(LlmUsageLine line, Instant createdAt) {
        ModelLlmCost cost = line.cost();
        LlmUsageEntity entity = new LlmUsageEntity();
        entity.setCompanyId(line.companyId());
        entity.setPurpose(line.purpose());
        entity.setReferenceType(line.referenceType());
        entity.setReferenceId(line.referenceId());
        entity.setModel(cost.model());
        entity.setListed(cost.listed());
        entity.setInputTokens(cost.tokens().billablePromptTokens());
        entity.setOutputTokens(cost.tokens().completionTokens());
        entity.setCachedTokens(cost.tokens().cachedTokens());
        entity.setInputPerMillionUzs(cost.rates().promptPerMillionUzs());
        entity.setOutputPerMillionUzs(cost.rates().completionPerMillionUzs());
        entity.setCachedPerMillionUzs(cost.rates().cachedPromptPerMillionUzs());
        entity.setMarkupBasisPoints(line.markupBasisPoints());
        entity.setInputUzs(cost.promptUzs());
        entity.setOutputUzs(cost.completionUzs());
        entity.setCachedUzs(cost.cachedUzs());
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
