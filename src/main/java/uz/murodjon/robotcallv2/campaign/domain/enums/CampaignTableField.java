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
    SCENARIO_ID("scenario_id") {
        /** {@code scenario} is a {@code @ManyToOne}: the entity has no {@code scenarioId} attribute. */
        @Override
        public String property() {
            return "scenario.id";
        }
    };

    private final String column;

    CampaignTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
