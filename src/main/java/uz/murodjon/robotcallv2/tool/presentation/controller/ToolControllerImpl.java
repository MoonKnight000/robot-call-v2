package uz.murodjon.robotcallv2.tool.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.dto.UpdateToolRequest;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;

import java.util.List;

@RestController
public class ToolControllerImpl implements ToolController {

    private final ToolUseCase toolUseCase;

    public ToolControllerImpl(ToolUseCase toolUseCase) {
        this.toolUseCase = toolUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ToolRow>> create(long companyId, CreateToolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(toolUseCase.createTool(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<List<ToolRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(toolUseCase.findTools(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ToolRow>>> filter(long companyId, ToolFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(toolUseCase.filterTools(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ToolRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(toolUseCase.findTool(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<ToolRow>> update(long companyId, long id, UpdateToolRequest request) {
        return ResponseEntity.ok(ResponseData.ok(toolUseCase.updateTool(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        toolUseCase.deleteTool(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }
}
