package uz.murodjon.robotcallv2.pack.application.port.output;

import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.List;

/**
 * Writes a pack for a business described in prose (UNIVERSAL-OPERATOR-PLAN E.1).
 *
 * <p>The split matters more than the model does. What the caller hears — the opening line, the
 * stages, the guardrails, the result fields, the test cases — is written here. What the agent
 * sends — an address, a method, a body schema — is not: those come from the API document
 * through the importer (B.2) and are handed in as {@code tools}, so the model can name a tool
 * in a stage but can never invent an endpoint.
 *
 * @param tools the tools the agent will have, as the importer built them; the model is told
 *              their names and descriptions so the stages can use them, and may not add any
 * @return the pack with its agent, scenario, test cases and conversion goal filled in, and its
 *         {@code tools} and {@code secrets} left empty for the service to put back
 */
public interface PackGeneratorPort {

    VerticalPack generate(String business, String language, List<Tool> tools);
}
