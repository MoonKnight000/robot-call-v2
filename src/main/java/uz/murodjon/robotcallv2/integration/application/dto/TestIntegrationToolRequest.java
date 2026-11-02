package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.tool.domain.entity.ToolOverride;

import java.util.Map;

/**
 * A dry run of one of the company's integration tools, as an agent's function form would have it.
 *
 * @param args      what the model would send for the inputs it fills
 * @param variables the call variables the tool reads ({@code phone}, {@code phone_last9} …), as a test call would have them
 * @param override  the form's inputs and answer fields, saved or not; null runs the tool as the catalog has it
 */
public record TestIntegrationToolRequest(
        Map<String, Object> args,
        Map<String, Object> variables,
        ToolOverride override
) {
}
