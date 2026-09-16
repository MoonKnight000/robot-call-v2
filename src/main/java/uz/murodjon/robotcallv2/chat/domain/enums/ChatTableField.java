package uz.murodjon.robotcallv2.chat.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns ChatFilter may sort the panel's conversation list by. */
public enum ChatTableField implements TableField {
    ID("id"),
    CREATED_AT("created_at"),
    LAST_MESSAGE_AT("last_message_at"),
    MESSAGE_COUNT("message_count");

    private final String column;

    ChatTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
