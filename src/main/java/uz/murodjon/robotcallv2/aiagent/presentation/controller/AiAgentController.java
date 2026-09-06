package uz.murodjon.robotcallv2.aiagent.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * AI agents — who speaks a call (V12).
 *
 * <p>An agent binds a scenario (what is said) to a voice, a persona, a model and a set of
 * SIP trunks. A campaign dials with one; an inbound route answers with one. Nothing else
 * in the API decides those settings any more.
 */
@RequestMapping("/api/ai-agents")
public interface AiAgentController {

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<AiAgentRow>> create(@Valid @RequestBody CreateAiAgentRequest request);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @PostMapping({"/filter", "/list"})
    ResponseEntity<ResponseData<PageableData<AiAgentRow>>> filter(@Valid @RequestBody AiAgentFilter filter);

    @PreAuthorize("hasAuthority('AI_AGENT_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> get(@PathVariable long id);

    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @PutMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<AiAgentRow>> update(@PathVariable long id,
                                                    @Valid @RequestBody UpdateAiAgentRequest request);

    /** 409 while any campaign or inbound route still runs this agent. */
    @PreAuthorize("hasAuthority('AI_AGENT_EDIT')")
    @DeleteMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@PathVariable long id);
}
