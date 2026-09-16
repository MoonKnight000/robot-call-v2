package uz.murodjon.robotcallv2.insight.application.port.output;

import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface AgentInsightRepository {

    /** Inserts a proposal without an id, replaces the one with it. */
    AgentInsight save(AgentInsight insight);

    AgentInsight findByCompanyIdAndId(long companyId, long insightId);

    PageableData<AgentInsight> findPageByCompanyId(long companyId, AgentInsightFilter filter);

    /** Whether this agent already has this finding open — what keeps the daily job from repeating itself. */
    boolean existsProposed(long aiAgentId, InsightType type, String title);

    /** The proposal waiting on that evaluation run, or null when no proposal started it. */
    AgentInsight findByEvalRunId(long evalRunId);
}
