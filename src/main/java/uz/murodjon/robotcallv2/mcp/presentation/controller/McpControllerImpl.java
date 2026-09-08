package uz.murodjon.robotcallv2.mcp.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRequest;
import uz.murodjon.robotcallv2.mcp.application.dto.McpConnectionRow;
import uz.murodjon.robotcallv2.mcp.application.port.input.McpUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class McpControllerImpl implements McpController {

    private final McpUseCase mcpUseCase;

    public McpControllerImpl(McpUseCase mcpUseCase) {
        this.mcpUseCase = mcpUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<McpConnectionRow>>> connections(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(mcpUseCase.findConnections(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<McpConnectionRow>> createConnection(long companyId,
                                                                          McpConnectionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(mcpUseCase.createConnection(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<McpConnectionRow>> updateConnection(long companyId, long id,
                                                                          McpConnectionRequest request) {
        return ResponseEntity.ok(ResponseData.ok(mcpUseCase.updateConnection(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteConnection(long companyId, long id) {
        mcpUseCase.deleteConnection(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<McpConnectionRow>> refreshConnection(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(mcpUseCase.refreshConnection(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> attachToAgent(long companyId, long id, long agentId) {
        mcpUseCase.attachToAgent(companyId, agentId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> detachFromAgent(long companyId, long id, long agentId) {
        mcpUseCase.detachFromAgent(companyId, agentId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
