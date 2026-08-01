package uz.murodjon.uysotvoice.shared.dialog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Tolerant {@link ReasonCode} mapping: an unrecognized stored value (written by an older
 * build of the enum) reads back as {@code null} instead of failing the whole row load —
 * losing one field is better than losing the {@code call_result} it belongs to.
 */
@Converter
public class ReasonCodeConverter implements AttributeConverter<ReasonCode, String> {

    @Override
    public String convertToDatabaseColumn(ReasonCode attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ReasonCode convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return ReasonCode.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
