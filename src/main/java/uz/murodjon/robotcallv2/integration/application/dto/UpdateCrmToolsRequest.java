package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * PUT /api/settings/integrations/tools body: the tools (by key) the voice agent is offered.
 * Every visible tool not listed is switched off; an empty list switches them all off.
 */
public record UpdateCrmToolsRequest(@NotNull List<String> enabledTools) {
}
