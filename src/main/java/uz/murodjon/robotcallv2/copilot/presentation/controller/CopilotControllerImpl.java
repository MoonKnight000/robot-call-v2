package uz.murodjon.robotcallv2.copilot.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.copilot.application.dto.AskCopilotRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.CopilotReplyResponse;
import uz.murodjon.robotcallv2.copilot.application.port.input.CopilotUseCase;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class CopilotControllerImpl implements CopilotController {

    private final CopilotUseCase copilotUseCase;

    public CopilotControllerImpl(CopilotUseCase copilotUseCase) {
        this.copilotUseCase = copilotUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CopilotReplyResponse>> ask(long companyId, AskCopilotRequest request) {
        return ResponseEntity.ok(ResponseData.ok(copilotUseCase.ask(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CopilotSession>>> filterSessions(long companyId,
                                                                                     CopilotSessionFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(copilotUseCase.filterSessions(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CopilotMessage>>> findMessages(long companyId, long sessionId) {
        return ResponseEntity.ok(ResponseData.ok(copilotUseCase.findMessages(companyId, sessionId)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteSession(long companyId, long sessionId) {
        copilotUseCase.deleteSession(companyId, sessionId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
