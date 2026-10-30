package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmToolRow;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationToolAdminRow;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateCrmToolsRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.tool.application.dto.TestToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tools an integration offers the voice agent: the catalog the platform admin keeps, and
 * what each company chose from it. A company that never chose follows a tool's default.
 */
public interface IntegrationToolUseCase {

    /** The tools the platform offers this company, each with its choice and whether its grants cover it. */
    List<CrmToolRow> findTools(long companyId);

    /** Saves which tools the agent is offered (the rest are switched off) and returns the result. */
    List<CrmToolRow> updateTools(long companyId, UpdateCrmToolsRequest request);

    /**
     * The model-facing names of the tools the agent may be offered for this company: on, offered by
     * the platform, and covered by its CRM grants. Not a statement that the company is connected.
     */
    Set<String> findEnabledToolNames(long companyId);

    /**
     * The HTTP tools the agent may be offered for this company: on, offered by the platform and
     * covered by its grants, already pointed at the company's own Uysot address and carrying its
     * token. Empty when the company is not connected. The tools belong to no company and are not
     * stored, so nothing about them may be saved.
     */
    List<Tool> findEnabledHttpTools(long companyId);

    /**
     * The model-facing text the platform admin set for built-in tools, by the name the model knows each by
     * ({@code CrmTool#toolName}). A tool left on the text in code is absent. The same for every company.
     */
    Map<String, String> findBuiltInModelDescriptions();

    /**
     * Platform admin: runs an HTTP tool of the catalog once against the admin's own company
     * account, as the model would, to see what it gets back. A write is really sent.
     */
    ToolTestResponse testCatalogTool(long adminCompanyId, long id, TestToolRequest request);

    /** Platform admin: the whole catalog of a provider, platform-disabled tools included. */
    List<IntegrationToolAdminRow> findCatalog(CrmProvider provider);

    /**
     * Platform admin ({@code adminCompanyId} is the admin's own company, for the audit trail): edits a
     * catalog entry. When the default changes, {@code applyTo} says whether
     * companies that never chose follow it ({@code ALL}) or keep today's value ({@code NEW_ONLY}).
     */
    IntegrationToolAdminRow updateCatalogTool(long adminCompanyId, long id, UpdateIntegrationToolRequest request);

    /**
     * Platform admin: adds an HTTP tool. When it starts enabled, {@code applyTo} says whether companies
     * that exist now get it ({@code ALL}) or only companies created later ({@code NEW_ONLY}).
     */
    IntegrationToolAdminRow createCatalogTool(long adminCompanyId, CreateIntegrationToolRequest request);

    /** Platform admin: removes an HTTP tool, and every company's choice about it. A built-in tool cannot be removed. */
    void deleteCatalogTool(long adminCompanyId, long id);
}
