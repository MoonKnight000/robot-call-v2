package uz.murodjon.robotcallv2.pack.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentTestCase;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;

import java.util.List;

/**
 * One industry, as data (UNIVERSAL-OPERATOR-PLAN C.1): everything an operator for it needs,
 * installed into a company in one step. The core stays industry-blind — a new industry is a
 * new pack, never new Java code.
 *
 * <p>The test cases and goal use their own features' types, so a pack says exactly what those
 * features would store; the fields only storage fills (ids, company, timestamps) are left out
 * of a pack and ignored on install. The tools are {@link PackTool}: the flat shape pack files
 * were written in, kept stable while the tool feature's model is grouped.
 *
 * @param catalogFeed    the product catalog the agent searches; null for a pack without one
 * @param conversionGoal created on install when the company has no goal under its key yet;
 *                       null for none
 * @param secrets        every {@code {{secrets.KEY}}} the tools read
 */
public record VerticalPack(
        String key,
        String name,
        String description,
        String industry,
        PackAgent agent,
        List<PackTool> tools,
        PackCatalogFeed catalogFeed,
        ConversionGoal conversionGoal,
        List<AgentTestCase> testCases,
        List<PackSecret> secrets
) {
    public VerticalPack {
        tools = tools == null ? List.of() : List.copyOf(tools);
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        secrets = secrets == null ? List.of() : List.copyOf(secrets);
    }
}
