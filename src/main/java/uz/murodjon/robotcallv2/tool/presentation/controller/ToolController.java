package uz.murodjon.robotcallv2.tool.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ImportToolsResponse;
import uz.murodjon.robotcallv2.tool.application.dto.OpenApiOperationRow;
import uz.murodjon.robotcallv2.tool.application.dto.TestToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
import uz.murodjon.robotcallv2.tool.application.dto.ToolTestResponse;
import uz.murodjon.robotcallv2.tool.application.dto.UpdateToolRequest;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;

import java.util.List;

@RequestMapping("/api/tools")
public interface ToolController {

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<ToolRow>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateToolRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<ToolRow>>> list(
            @CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<ToolRow>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ToolFilter filter);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<ToolRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<ToolRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateToolRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /**
     * Calls the tool once, as the model would, and returns what the model would be handed:
     * the panel's "Test" button. The request really goes out: a tool that writes (a POST
     * creating a lead) writes on a test too.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/test")
    ResponseEntity<ResponseData<ToolTestResponse>> test(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody(required = false) TestToolRequest request);

    /**
     * The operations an OpenAPI 3 document describes (UNIVERSAL-OPERATOR-PLAN B.2), to choose
     * which become tools. Creates nothing; {@code operationIds} is ignored. A POST because the
     * document itself may be in the body.
     */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/import/operations")
    ResponseEntity<ResponseData<List<OpenApiOperationRow>>> importOperations(
            @Valid @RequestBody ImportToolsRequest request);

    /** One tool per chosen operation, all or none; the secrets they read are named, not created. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping("/import")
    ResponseEntity<ResponseData<ImportToolsResponse>> importTools(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ImportToolsRequest request);
}
