package uz.murodjon.robotcallv2.integration.application.port.output;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IntegrationToolRepository {

    /** Every tool of the provider in the admin's order, platform-disabled ones included. */
    List<IntegrationTool> findByProvider(CrmProvider provider);

    Optional<IntegrationTool> findById(long id);

    /** Saves what the admin may edit (label, description, group, defaults, order; an HTTP tool's grant and definition too). */
    IntegrationTool update(IntegrationTool tool);

    /** Stores a new tool; the id on {@code tool} is ignored. */
    IntegrationTool create(IntegrationTool tool);

    /** Removes the tool and, with it, every company's choice about it. */
    void deleteById(long id);

    /** The company's own choices, tool id to enabled; a tool it never chose for is absent. */
    Map<Long, Boolean> findChoices(long companyId);

    void saveChoice(long companyId, long toolId, boolean enabled);

    /** Forgets the company's choice, so the tool follows its default again. */
    void deleteChoice(long companyId, long toolId);

    /**
     * Makes {@code enabled} the choice of every company that has none for the tool, so a
     * later change of the default does not reach them.
     *
     * @return how many companies were pinned
     */
    int pinCompaniesWithoutChoice(long toolId, boolean enabled);
}
