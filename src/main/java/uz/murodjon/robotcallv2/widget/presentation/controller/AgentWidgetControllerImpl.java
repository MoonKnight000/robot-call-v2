package uz.murodjon.robotcallv2.widget.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.widget.application.dto.*;
import uz.murodjon.robotcallv2.widget.application.port.input.AgentWidgetUseCase;

import java.util.List;

@RestController
public class AgentWidgetControllerImpl implements AgentWidgetController {

    private final AgentWidgetUseCase agentWidgetUseCase;

    public AgentWidgetControllerImpl(AgentWidgetUseCase agentWidgetUseCase) {
        this.agentWidgetUseCase = agentWidgetUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<WidgetRow>> create(long companyId, long agentId,
                                                          CreateWidgetRequest request) {
        return ResponseEntity.ok(ResponseData.ok(agentWidgetUseCase.createWidget(companyId, agentId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<WidgetRow>>> listByAgent(long companyId, long agentId) {
        return ResponseEntity.ok(ResponseData.ok(agentWidgetUseCase.findWidgetsByAgentId(companyId, agentId)));
    }

    @Override
    public ResponseEntity<ResponseData<WidgetRow>> get(long companyId, long widgetId) {
        return ResponseEntity.ok(ResponseData.ok(agentWidgetUseCase.findWidget(companyId, widgetId)));
    }

    @Override
    public ResponseEntity<ResponseData<WidgetRow>> update(long companyId, long widgetId,
                                                          UpdateWidgetRequest request) {
        return ResponseEntity.ok(ResponseData.ok(agentWidgetUseCase.updateWidget(companyId, widgetId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long widgetId) {
        agentWidgetUseCase.deleteWidget(companyId, widgetId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<PublicWidgetConfigResponse>> getPublicConfig(String widgetKey, String origin) {
        return ResponseEntity.ok(ResponseData.ok(agentWidgetUseCase.findPublicConfig(widgetKey, origin)));
    }

    @Override
    public ResponseEntity<ResponseData<PublicWidgetSessionResponse>> startPublicSession(
            String widgetKey, String language, String origin) {
        return ResponseEntity.ok(ResponseData.ok(
                agentWidgetUseCase.startPublicSession(widgetKey, origin, language)));
    }
}
