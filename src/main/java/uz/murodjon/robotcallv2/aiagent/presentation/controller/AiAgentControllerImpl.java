package uz.murodjon.robotcallv2.aiagent.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class AiAgentControllerImpl implements AiAgentController {

    private final AiAgentUseCase aiAgentUseCase;

    public AiAgentControllerImpl(AiAgentUseCase aiAgentUseCase) {
        this.aiAgentUseCase = aiAgentUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> create(CreateAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.createAgent(request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AiAgentRow>>> filter(AiAgentFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.filterAgents(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.findAgentRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> update(long id, UpdateAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateAgent(id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long id) {
        aiAgentUseCase.deleteAgent(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
