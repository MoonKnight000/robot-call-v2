package uz.murodjon.robotcallv2.mcp.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRequest;
import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRow;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * MCP servers a company runs elsewhere, offered to its agents as tools.
 *
 * <p>The difference from {@code /api/tools} is who writes the description. A tool there is
 * one REST endpoint a company describes field by field; an MCP server describes its own
 * tools, so a company that already runs one gets all of them by pasting a URL.
 *
 * <p>Two rules are worth knowing before wiring a screen to this. The server is contacted
 * <b>while the connection is being saved</b>, so a bad URL or a refused token is an answer
 * on the form rather than a tool that silently never appears on a call. And a tool the
 * server marks destructive is never offered to the model, whatever {@code allowWrites}
 * says — {@code usableToolCount} is what actually reached the agent.
 */
@RequestMapping("/api/mcp")
public interface McpController {

    @GetMapping("/connections")
    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    ResponseEntity<ResponseData<List<McpConnectionRow>>> connections(@CurrentCompanyId long companyId);

    @PostMapping("/connections")
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    ResponseEntity<ResponseData<McpConnectionRow>> createConnection(
            @CurrentCompanyId long companyId, @Valid @RequestBody McpConnectionRequest request);

    @PutMapping("/connections/{id}")
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    ResponseEntity<ResponseData<McpConnectionRow>> updateConnection(
            @CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody McpConnectionRequest request);

    @DeleteMapping("/connections/{id}")
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    ResponseEntity<ResponseData<Void>> deleteConnection(@CurrentCompanyId long companyId, @PathVariable long id);

    /** Re-reads the server's tool list — after its tools change, or to retry a failure. */
    @PostMapping("/connections/{id}/refresh")
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    ResponseEntity<ResponseData<McpConnectionRow>> refreshConnection(
            @CurrentCompanyId long companyId, @PathVariable long id);

    /** Lets one agent use one connection. A collections agent and a support agent share a company, not a toolbox. */
    @PostMapping("/connections/{id}/agents/{agentId}")
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    ResponseEntity<ResponseData<Void>> attachToAgent(@CurrentCompanyId long companyId,
                                                     @PathVariable long id, @PathVariable long agentId);

    @DeleteMapping("/connections/{id}/agents/{agentId}")
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    ResponseEntity<ResponseData<Void>> detachFromAgent(@CurrentCompanyId long companyId,
                                                       @PathVariable long id, @PathVariable long agentId);
}
