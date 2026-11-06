package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.LlmUsageRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.LlmUsageLine;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelSpend;
import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.mapper.LlmUsageEntityMapper;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.LlmUsageJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class LlmUsageRepositoryAdapter implements LlmUsageRepository {

    private final LlmUsageJpaRepository jpaRepository;
    private final LlmUsageEntityMapper mapper;

    public LlmUsageRepositoryAdapter(LlmUsageJpaRepository jpaRepository, LlmUsageEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void saveAll(List<LlmUsageLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        jpaRepository.saveAll(lines.stream().map(line -> mapper.toLlmUsageEntity(line, now)).toList());
    }

    @Override
    public List<ModelSpend> findModelSpend(long companyId, Instant from, Instant to) {
        return jpaRepository.findModelSpend(companyId, from, to).stream()
                .map(row -> new ModelSpend(
                        (String) row[0],
                        LlmUsagePurpose.valueOf((String) row[1]),
                        Boolean.TRUE.equals(row[2]),
                        number(row[3]), number(row[4]), number(row[5]),
                        number(row[6]), number(row[7]), number(row[8])))
                .toList();
    }

    /** SUM over a bigint answers as BigDecimal or BigInteger depending on the driver path. */
    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
