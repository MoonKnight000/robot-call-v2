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
    public ResponseEntity<ResponseData<AiAgentRow>> create(long companyId, CreateAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.createAgent(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<AiAgentRow>>> filter(long companyId, AiAgentFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.filterAgents(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.findAgentRow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<AiAgentRow>> update(long companyId, long id, UpdateAiAgentRequest request) {
        return ResponseEntity.ok(ResponseData.ok(aiAgentUseCase.updateAgent(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        aiAgentUseCase.deleteAgent(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
