package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@link CampaignFilter} may sort the campaign list by. */
public enum CampaignTableField implements TableField {
    ID("id"),
    NAME("name"),
    TYPE("type"),
    STATUS("status");

    private final String column;

    CampaignTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
