package uz.murodjon.robotcallv2.copilot.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.copilot.application.dto.AskCopilotRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.CopilotReplyResponse;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * The panel's AI copilot (docs/api/copilot.md). It reads agents, evaluations and calls with
 * the tools the caller's permissions open, and writes agent changes to drafts only — publishing
 * stays with a person. Every conversation is the asking user's own.
 */
@RequestMapping("/api/copilot")
public interface CopilotController {

    /** One question; {@code sessionId} null starts a conversation. Answers once the model is done. */
    @PreAuthorize("hasAnyAuthority('AI_AGENT_READ', 'REPORT_READ')")
    @PostMapping("/ask")
    ResponseEntity<ResponseData<CopilotReplyResponse>> ask(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody AskCopilotRequest request);

    @PreAuthorize("hasAnyAuthority('AI_AGENT_READ', 'REPORT_READ')")
    @PostMapping("/sessions/filter")
    ResponseEntity<ResponseData<PageableData<CopilotSession>>> filterSessions(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CopilotSessionFilter filter);

    /** The conversation in full, the tool calls between the answers included. */
    @PreAuthorize("hasAnyAuthority('AI_AGENT_READ', 'REPORT_READ')")
    @GetMapping("/sessions/{sessionId:\\d+}/messages")
    ResponseEntity<ResponseData<List<CopilotMessage>>> findMessages(
            @CurrentCompanyId long companyId,
            @PathVariable long sessionId);

    @PreAuthorize("hasAnyAuthority('AI_AGENT_READ', 'REPORT_READ')")
    @DeleteMapping("/sessions/{sessionId:\\d+}")
    ResponseEntity<ResponseData<Void>> deleteSession(
            @CurrentCompanyId long companyId,
            @PathVariable long sessionId);
}
