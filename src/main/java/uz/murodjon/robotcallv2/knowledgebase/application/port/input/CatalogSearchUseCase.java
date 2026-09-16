package uz.murodjon.robotcallv2.knowledgebase.application.port.input;

import uz.murodjon.robotcallv2.knowledgebase.domain.entity.CatalogMatch;

import java.util.List;

/**
 * What the dialog asks the company's catalog during a call (UNIVERSAL-OPERATOR-PLAN B.4):
 * the rows that match what the caller named, so an order is placed on a SKU the catalog holds.
 */
public interface CatalogSearchUseCase {

    /**
     * The best {@code limit} rows for what the caller said, best first; empty when nothing
     * matches. Never throws: a caller is waiting, so a failed search is an empty one.
     *
     * @param agentId the call's agent; rows of other agents' catalogs are left out, company-wide ones kept
     */
    List<CatalogMatch> findCatalogMatches(long companyId, Long agentId, String query, int limit);

    /** Whether the agent has an indexed catalog — whether it is offered the search at all. */
    boolean existsByCompanyIdAndAgentId(long companyId, Long agentId);
}
