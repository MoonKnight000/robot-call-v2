package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.integration.application.dto.CreateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmToolRow;
import uz.murodjon.robotcallv2.integration.application.dto.IntegrationToolAdminRow;
import uz.murodjon.robotcallv2.integration.application.dto.TestIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateCrmToolsRequest;
import uz.murodjon.robotcallv2.integration.application.dto.UpdateIntegrationToolRequest;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.tool.application.dto.TestToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;
import java.util.Map;

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
     * The HTTP tools the agent may be offered for this company: on, offered by the platform and
     * covered by its grants, already pointed at the company's own Uysot address and carrying its
     * token. Empty when the company is not connected. The tools belong to no company and are not
     * stored, so nothing about them may be saved.
     */
    List<Tool> findEnabledHttpTools(long companyId);

    /**
     * Runs one of {@link #findEnabledHttpTools} once for this company, outside any call, and returns what the
     * integration answered (the tool's {@code raw} body). Only a read-only tool runs: this is how a campaign
     * reads a list, and reading must not change the CRM. No transaction: the request goes out over the network.
     *
     * @throws uz.murodjon.robotcallv2.shared.exception.NotFoundException when the company has no such tool on
     * @throws uz.murodjon.robotcallv2.shared.exception.ValidationException when the tool is not read-only
     */
    ToolTestResponse runEnabledReadTool(long companyId, String toolName, Map<String, Object> arguments);

    /**
     * Runs one of {@link #findEnabledHttpTools} once against the company's account with an agent function's
     * inputs and answer fields applied — the "Run a test" of the function form. Only a read-only tool runs:
     * a test must not change the CRM. No transaction: the request goes out over the network.
     *
     * @throws uz.murodjon.robotcallv2.shared.exception.NotFoundException when the company has no such tool on
     * @throws uz.murodjon.robotcallv2.shared.exception.ValidationException when the tool is not read-only
     */
    ToolTestResponse testEnabledTool(long companyId, String toolName, TestIntegrationToolRequest request);

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
