package uz.murodjon.robotcallv2.integration.domain.entity;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmToolGroup;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationToolKind;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

/** Builder for {@link IntegrationTool}; start from {@link IntegrationTool#toBuilder()} to change a few fields. */
public final class IntegrationToolBuilder {

    private long id;
    private CrmProvider provider;
    private String key;
    private IntegrationToolKind kind = IntegrationToolKind.BUILT_IN;
    private CrmToolGroup group;
    private String label;
    private String description;
    private CrmPermission permission;
    private CrmScopeLevel scope;
    private boolean defaultEnabled = true;
    private boolean platformEnabled = true;
    private int sortOrder;
    private Tool definition;
    private String modelDescription;

    IntegrationToolBuilder() {
    }

    IntegrationToolBuilder(IntegrationTool tool) {
        this.id = tool.id();
        this.provider = tool.provider();
        this.key = tool.key();
        this.kind = tool.kind();
        this.group = tool.group();
        this.label = tool.label();
        this.description = tool.description();
        this.permission = tool.permission();
        this.scope = tool.scope();
        this.defaultEnabled = tool.defaultEnabled();
        this.platformEnabled = tool.platformEnabled();
        this.sortOrder = tool.sortOrder();
        this.definition = tool.definition();
        this.modelDescription = tool.modelDescription();
    }

    public IntegrationToolBuilder id(long id) {
        this.id = id;
        return this;
    }

    public IntegrationToolBuilder provider(CrmProvider provider) {
        this.provider = provider;
        return this;
    }

    public IntegrationToolBuilder key(String key) {
        this.key = key;
        return this;
    }

    public IntegrationToolBuilder kind(IntegrationToolKind kind) {
        this.kind = kind;
        return this;
    }

    public IntegrationToolBuilder group(CrmToolGroup group) {
        this.group = group;
        return this;
    }

    public IntegrationToolBuilder label(String label) {
        this.label = label;
        return this;
    }

    public IntegrationToolBuilder description(String description) {
        this.description = description;
        return this;
    }

    public IntegrationToolBuilder permission(CrmPermission permission) {
        this.permission = permission;
        return this;
    }

    public IntegrationToolBuilder scope(CrmScopeLevel scope) {
        this.scope = scope;
        return this;
    }

    public IntegrationToolBuilder defaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
        return this;
    }

    public IntegrationToolBuilder platformEnabled(boolean platformEnabled) {
        this.platformEnabled = platformEnabled;
        return this;
    }

    public IntegrationToolBuilder sortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public IntegrationToolBuilder definition(Tool definition) {
        this.definition = definition;
        return this;
    }

    public IntegrationToolBuilder modelDescription(String modelDescription) {
        this.modelDescription = modelDescription;
        return this;
    }

    public IntegrationTool build() {
        return new IntegrationTool(id, provider, key, kind, group, label, description, permission, scope,
                defaultEnabled, platformEnabled, sortOrder, definition, modelDescription);
    }
}
