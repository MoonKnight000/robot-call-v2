package uz.murodjon.robotcallv2.inbound.application.port.output;

import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRuleFilter;

import java.util.List;

public interface InboundCallerRuleRepository {

    long create(long companyId, InboundCallerRule rule);

    void update(long companyId, long id, InboundCallerRule rule);

    void delete(long companyId, long id);

    InboundCallerRule find(long companyId, long id);

    boolean existsByPrefix(long companyId, String phonePrefix, Long excludeId);

    List<InboundCallerRule> findAll(long companyId, InboundCallerRuleFilter filter);

    long count(long companyId);

    List<InboundCallerRule> findByCompanyId(long companyId);
}
