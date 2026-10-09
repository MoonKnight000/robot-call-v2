package uz.murodjon.robotcallv2.mcp.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.mcp.application.port.input.McpServerUseCase;
import uz.murodjon.robotcallv2.shared.api.JsonRpcResponses;

@RestController
public class McpServerControllerImpl implements McpServerController {

    private final McpServerUseCase mcpServerUseCase;

    public McpServerControllerImpl(McpServerUseCase mcpServerUseCase) {
        this.mcpServerUseCase = mcpServerUseCase;
    }

    @Override
    public ResponseEntity<String> handleMessage(long companyId, String body) {
        return JsonRpcResponses.of(mcpServerUseCase.handleMessage(companyId, body));
    }
}
