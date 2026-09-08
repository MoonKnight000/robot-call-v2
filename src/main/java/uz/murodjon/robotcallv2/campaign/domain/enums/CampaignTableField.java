package uz.murodjon.robotcallv2.campaign.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns {@link uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter} may sort the campaign list by. */
public enum CampaignTableField implements TableField {
    ID("id"),
    NAME("name"),
    TYPE("type"),
    STATUS("status"),
    CREATED_AT("created_at"),
    LAST_RUN_AT("last_run_at"),
    AI_AGENT_ID("ai_agent_id", "aiAgent.id");

    private final String column;
    private final String property;

    CampaignTableField(String column) {
        this(column, null);
    }

    /** {@code property} where the entity maps the column as an association, not a plain field. */
    CampaignTableField(String column, String property) {
        this.column = column;
        this.property = property;
    }

    @Override
    public String column() {
        return column;
    }

    @Override
    public String property() {
        return property != null ? property : TableField.super.property();
    }
}
