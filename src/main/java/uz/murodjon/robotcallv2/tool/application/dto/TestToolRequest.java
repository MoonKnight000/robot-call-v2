package uz.murodjon.robotcallv2.tool.application.dto;

import java.util.Map;

/**
 * A dry run of a tool from the panel's "Test" button ({@code POST /api/tools/{id}/test}).
 *
 * @param args      what the model would pass: one entry per {@code LLM Prompt} parameter
 * @param variables the call variables a {@code Dynamic Variable} parameter reads, the way a
 *                  real call would have them ({@code phone}, {@code clientName}, ...)
 */
public record TestToolRequest(
        Map<String, Object> args,
        Map<String, Object> variables
) {
}
