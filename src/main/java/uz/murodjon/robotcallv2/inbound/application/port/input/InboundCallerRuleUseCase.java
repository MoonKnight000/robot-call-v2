package uz.murodjon.robotcallv2.inbound.application.port.input;

import uz.murodjon.robotcallv2.inbound.application.dto.SaveCallerRuleRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRuleFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface InboundCallerRuleUseCase {

    InboundCallerRule create(long companyId, SaveCallerRuleRequest request);

    InboundCallerRule update(long companyId, long id, SaveCallerRuleRequest request);

    void delete(long companyId, long id);

    PageableData<InboundCallerRule> list(long companyId, InboundCallerRuleFilter filter);

    /** The rule that applies to this caller (longest prefix), or null. */
    InboundCallerRule findMatchingRule(long companyId, String callerNumber);
}
