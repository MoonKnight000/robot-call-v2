package uz.murodjon.robotcallv2.tool.application.dto;

import java.util.List;

/**
 * @param secretKeys every secret the new tools read — set them in {@code /api/secrets} before
 *                   the tools are tried; the import creates none
 */
public record ImportToolsResponse(List<ToolRow> tools, List<String> secretKeys) {
}
