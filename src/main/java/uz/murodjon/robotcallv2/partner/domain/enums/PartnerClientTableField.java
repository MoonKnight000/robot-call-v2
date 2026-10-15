package uz.murodjon.robotcallv2.partner.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns PartnerClientFilter may sort a partner's client list by. */
public enum PartnerClientTableField implements TableField {
    CLIENT_COMPANY_ID("client_company_id"),
    CREATED_AT("created_at");

    private final String column;

    PartnerClientTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
