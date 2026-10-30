package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Uysot tools of one call ({@link RealEstateTools}, {@link CrmLeadTools}), cut down to
 * the ones the company switched on. Shared by both pipelines so the cascade and the
 * realtime engine offer the same set.
 */
final class CrmToolCallbacks {

    private CrmToolCallbacks() {
    }

    /**
     * @param enabledToolNames model-facing names from {@code CrmCatalogUseCase#findEnabledToolNames};
     *                         empty = the company is not on Uysot, or turned everything off
     * @param descriptions     model-facing text the platform admin set, by tool name; a tool not in it keeps
     *                         the text in code
     */
    static List<ToolCallback> build(DialogOutcomeSink session, long companyId, Set<String> enabledToolNames,
                                    Map<String, String> descriptions, CrmCatalogUseCase catalog, CrmUseCase crm,
                                    ClientMemoryUseCase memory) {
        if (enabledToolNames.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> all = new ArrayList<>(List.of(MethodToolCallbackProvider.builder()
                .toolObjects(new RealEstateTools(session, companyId, catalog, memory))
                .build().getToolCallbacks()));
        if (crm != null) {
            all.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new CrmLeadTools(session, companyId, crm, catalog))
                    .build().getToolCallbacks()));
        }
        return all.stream()
                .filter(callback -> enabledToolNames.contains(callback.getToolDefinition().name()))
                .map(callback -> descriptions.containsKey(callback.getToolDefinition().name())
                        ? (ToolCallback) new DescribedToolCallback(callback, descriptions.get(callback.getToolDefinition().name()))
                        : callback)
                .toList();
    }
}
