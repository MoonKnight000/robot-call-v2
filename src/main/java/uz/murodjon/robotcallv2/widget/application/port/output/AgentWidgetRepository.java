package uz.murodjon.robotcallv2.widget.application.port.output;

import uz.murodjon.robotcallv2.widget.domain.entity.AgentWidget;

import java.util.List;
import java.util.Optional;

public interface AgentWidgetRepository {

    AgentWidget save(AgentWidget widget);

    Optional<AgentWidget> findByCompanyIdAndId(long companyId, long id);

    /** Keyed without a company on purpose — the public endpoint has no tenant to scope by. */
    Optional<AgentWidget> findByWidgetKey(String widgetKey);

    List<AgentWidget> findByCompanyIdAndAgentId(long companyId, long agentId);

    void deleteByCompanyIdAndId(long companyId, long id);
}
