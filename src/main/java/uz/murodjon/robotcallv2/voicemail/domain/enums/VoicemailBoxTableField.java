package uz.murodjon.robotcallv2.voicemail.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns VoicemailBoxFilter may sort the box list by. */
public enum VoicemailBoxTableField implements TableField {
    ID("id"),
    NAME("name"),
    CREATED_AT("created_at");

    private final String column;

    VoicemailBoxTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
