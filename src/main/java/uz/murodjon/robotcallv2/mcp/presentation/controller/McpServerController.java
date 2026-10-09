package uz.murodjon.robotcallv2.mcp.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;

/**
 * The platform's own MCP server, for Claude Code, Cursor and other MCP clients — Streamable
 * HTTP, stateless, authenticated by {@code X-Api-Key} (or a Bearer token). Every tool checks
 * its own permission, so any authenticated caller may connect and sees only what its
 * permissions open.
 *
 * <p>The body is taken and returned as raw JSON-RPC text: the answer is the JSON-RPC envelope,
 * not {@code ResponseData}, and a notification is acknowledged with {@code 202} and no body.
 * There is no server-to-client stream, so {@code GET} is not offered.
 */
@RequestMapping("/api/mcp-server")
public interface McpServerController {

    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> handleMessage(
            @CurrentCompanyId long companyId,
            @RequestBody String body);
}
