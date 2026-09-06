package uz.murodjon.robotcallv2.knowledgebase.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum KnowledgeTableField implements TableField {
    ID("id"),
    KEY("item_key"),
    TOPIC("topic"),
    TITLE("title"),
    CREATED_AT("created_at");

    private final String column;

    KnowledgeTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
