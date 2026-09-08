package uz.murodjon.robotcallv2.tool.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.tool.application.dto.CreateToolRequest;
import uz.murodjon.robotcallv2.tool.application.dto.ToolRow;
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
}
