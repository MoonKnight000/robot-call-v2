package uz.murodjon.robotcallv2.voicemail.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns VoicemailMessageFilter may sort the message list by. */
public enum VoicemailMessageTableField implements TableField {
    ID("id"),
    CREATED_AT("created_at"),
    DURATION_SEC("duration_sec");

    private final String column;

    VoicemailMessageTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
