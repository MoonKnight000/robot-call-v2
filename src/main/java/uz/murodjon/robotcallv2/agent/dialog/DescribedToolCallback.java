package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** A tool callback under a different description: what the model reads changes, how it runs does not. */
final class DescribedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolDefinition definition;

    DescribedToolCallback(ToolCallback delegate, String description) {
        this.delegate = delegate;
        ToolDefinition original = delegate.getToolDefinition();
        this.definition = ToolDefinition.builder()
                .name(original.name())
                .description(description)
                .inputSchema(original.inputSchema())
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }
}
