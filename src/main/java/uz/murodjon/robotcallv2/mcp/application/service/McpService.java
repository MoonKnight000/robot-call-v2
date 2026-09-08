package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRequest;
import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRow;
import uz.murodjon.robotcallv2.mcp.application.mapper.McpMapper;
import uz.murodjon.robotcallv2.mcp.application.port.input.McpUseCase;
import uz.murodjon.robotcallv2.mcp.application.port.output.McpClientPort;
import uz.murodjon.robotcallv2.mcp.application.port.output.McpConnectionRepository;
import uz.murodjon.robotcallv2.mcp.application.port.output.McpToolExecutionRepository;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpToolExecution;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpAuthType;
import uz.murodjon.robotcallv2.mcp.domain.enums.McpConnectionStatus;
import uz.murodjon.robotcallv2.mcp.domain.service.McpToolGuard;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A company's MCP servers: saved, refreshed, and asked to do things during a call.
 *
 * <p>Tool discovery is eager and its result is stored on the connection. The alternative —
 * asking the server for its tool list at the start of every call — would put somebody
 * else's uptime between the caller answering and the agent speaking.
 */
@Service
public class McpService implements McpUseCase {

    private static final Logger log = LoggerFactory.getLogger(McpService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpConnectionRepository connectionRepository;
    private final McpToolExecutionRepository executionRepository;
    private final McpClientPort client;
    private final McpMapper mapper;
    private final SecretUseCase secretUseCase;

    public McpService(McpConnectionRepository connectionRepository,
                      McpToolExecutionRepository executionRepository,
                      McpClientPort client,
                      McpMapper mapper,
                      SecretUseCase secretUseCase) {
        this.connectionRepository = connectionRepository;
        this.executionRepository = executionRepository;
        this.client = client;
        this.mapper = mapper;
        this.secretUseCase = secretUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<McpConnectionRow> findConnections(long companyId) {
        return connectionRepository.findByCompanyId(companyId).stream().map(mapper::toRow).toList();
    }

    @Override
    @Transactional
    public McpConnectionRow createConnection(long companyId, McpConnectionRequest request) {
        String name = request.name().trim();
        connectionRepository.findByCompanyIdAndName(companyId, name).ifPresent(existing -> {
            throw new ConflictException(ErrorCode.MCP_CONNECTION_NAME_EXISTS, name);
        });
        McpConnection saved = connectionRepository.save(McpConnection.pending(companyId, name,
                requireCallableUrl(request.url(), name), authTypeOf(request), request.secretKey(),
                Boolean.TRUE.equals(request.allowWrites())));
        return mapper.toRow(refresh(saved));
    }

    @Override
    @Transactional
    public McpConnectionRow updateConnection(long companyId, long id, McpConnectionRequest request) {
        McpConnection existing = requireConnection(companyId, id);
        String name = request.name().trim();
        connectionRepository.findByCompanyIdAndName(companyId, name)
                .filter(other -> !other.id().equals(existing.id()))
                .ifPresent(other -> {
                    throw new ConflictException(ErrorCode.MCP_CONNECTION_NAME_EXISTS, name);
                });

        McpConnection updated = new McpConnection(existing.id(), companyId, name,
                requireCallableUrl(request.url(), name), authTypeOf(request), request.secretKey(),
                McpConnectionStatus.PENDING, existing.toolCount(), existing.usableToolCount(),
                Boolean.TRUE.equals(request.allowWrites()), existing.toolsJson(), null,
                existing.refreshedAt(), existing.createdAt(), null);
        return mapper.toRow(refresh(connectionRepository.save(updated)));
    }

    @Override
    @Transactional
    public void deleteConnection(long companyId, long id) {
        requireConnection(companyId, id);
        connectionRepository.deleteByCompanyIdAndId(companyId, id);
    }

    @Override
    @Transactional
    public McpConnectionRow refreshConnection(long companyId, long id) {
        return mapper.toRow(refresh(requireConnection(companyId, id)));
    }

    @Override
    @Transactional
    public void attachToAgent(long companyId, long agentId, long connectionId) {
        requireConnection(companyId, connectionId);
        connectionRepository.attachToAgent(agentId, connectionId);
    }

    @Override
    @Transactional
    public void detachFromAgent(long companyId, long agentId, long connectionId) {
        requireConnection(companyId, connectionId);
        connectionRepository.detachFromAgent(agentId, connectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<McpConnection, List<McpTool>> findAgentTools(long companyId, long agentId) {
        Map<McpConnection, List<McpTool>> byConnection = new LinkedHashMap<>();
        for (McpConnection connection : connectionRepository.findByAgentId(companyId, agentId)) {
            if (!connection.isUsable()) {
                continue;
            }
            List<McpTool> usable = readTools(connection);
            if (!usable.isEmpty()) {
                byConnection.put(connection, usable);
            }
        }
        return byConnection;
    }

    @Override
    public String callTool(long companyId, long connectionId, Long callAttemptId, String toolName,
                           Map<String, Object> arguments) {
        McpConnection connection = connectionRepository.findByCompanyIdAndId(companyId, connectionId)
                .orElse(null);
        if (connection == null || !connection.isUsable()) {
            return "Tool is unavailable.";
        }
        String args = String.valueOf(arguments);
        long startedAt = System.currentTimeMillis();
        try {
            String result = client.callTool(connection, bearerToken(connection), toolName, arguments);
            executionRepository.record(McpToolExecution.succeeded(companyId, connectionId, callAttemptId,
                    toolName, elapsed(startedAt), args, result));
            return result;
        } catch (Exception e) {
            executionRepository.record(McpToolExecution.failed(companyId, connectionId, callAttemptId,
                    toolName, elapsed(startedAt), args, String.valueOf(e.getMessage())));
            log.warn("MCP tool {} on {} failed: {}", toolName, connection.name(), e.getMessage());
            // Said out loud, not thrown: the model has to carry on the conversation
            // without this answer, and it cannot do that with a stack trace.
            return "Tool is unavailable.";
        }
    }

    /**
     * Talks to the server and writes down what happened, whichever way it went.
     *
     * <p>A failure is stored on the connection rather than thrown: an operator needs to
     * read why it is not working on the connections screen, and a 502 from a POST tells
     * them that once and then disappears.
     */
    private McpConnection refresh(McpConnection connection) {
        try {
            List<McpTool> discovered = client.listTools(connection, bearerToken(connection));
            List<McpTool> usable = McpToolGuard.filterUsable(discovered, connection.allowWrites());
            log.info("MCP connection {} of company {}: {} tools, {} usable",
                    connection.name(), connection.companyId(), discovered.size(), usable.size());
            // Only the usable ones are stored. A call reads this row and offers exactly
            // what is in it, so a tool that must never be reachable is not written down
            // in the first place rather than filtered again later.
            return connectionRepository.save(
                    connection.connected(discovered.size(), usable.size(), writeTools(usable)));
        } catch (ExternalServiceException e) {
            McpConnectionStatus status = e.code() == ErrorCode.MCP_AUTH_REQUIRED
                    ? McpConnectionStatus.AUTH_REQUIRED
                    : McpConnectionStatus.ERROR;
            return connectionRepository.save(connection.failed(status, e.getMessage()));
        } catch (Exception e) {
            return connectionRepository.save(
                    connection.failed(McpConnectionStatus.ERROR, String.valueOf(e.getMessage())));
        }
    }

    /**
     * The tools stored on a connection at its last refresh.
     *
     * <p>An unreadable row answers empty rather than throwing: one connection whose JSON
     * somebody hand-edited must not cost the call every other tool it has.
     */
    private List<McpTool> readTools(McpConnection connection) {
        if (connection.toolsJson() == null || connection.toolsJson().isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(connection.toolsJson(), McpTool[].class));
        } catch (Exception e) {
            log.warn("MCP connection {} has an unreadable tool list: {}",
                    connection.name(), e.getMessage());
            return List.of();
        }
    }

    private String writeTools(List<McpTool> tools) {
        try {
            return MAPPER.writeValueAsString(tools);
        } catch (Exception e) {
            // The counts are still worth storing; the connection simply offers no tools
            // until the next refresh works.
            log.warn("MCP tool list could not be stored: {}", e.getMessage());
            return null;
        }
    }

    /** The bearer token this connection authenticates with, or null when it does not. */
    private String bearerToken(McpConnection connection) {
        if (connection.authType() != McpAuthType.BEARER
                || connection.secretKey() == null || connection.secretKey().isBlank()) {
            return null;
        }
        return secretUseCase.findDecryptedSecrets(connection.companyId()).get(connection.secretKey());
    }

    /**
     * HTTPS only, and never an address on this network.
     *
     * <p>Plain HTTP is refused rather than allowed with a warning: the bearer token goes
     * in a header on every request, and a company pasting an {@code http://} URL would be
     * handing it to anyone on the path.
     */
    private static String requireCallableUrl(String url, String name) {
        URI uri = PublicUrlGuard.parsePublic(url);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ValidationException(ErrorCode.MCP_URL_INVALID, name);
        }
        return uri.toString();
    }

    private static McpAuthType authTypeOf(McpConnectionRequest request) {
        return request.authType() != null ? request.authType() : McpAuthType.NONE;
    }

    private McpConnection requireConnection(long companyId, long id) {
        return connectionRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MCP_CONNECTION_NOT_FOUND, id));
    }

    private static int elapsed(long startedAt) {
        return (int) (System.currentTimeMillis() - startedAt);
    }
}
