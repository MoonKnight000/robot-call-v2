package uz.murodjon.robotcallv2.webhook.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum WebhookDeliveryTableField implements TableField {
    CREATED_AT("created_at"),
    ATTEMPTS("attempts");

    private final String column;

    WebhookDeliveryTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
