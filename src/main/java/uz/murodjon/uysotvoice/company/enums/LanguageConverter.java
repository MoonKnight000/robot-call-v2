package uz.murodjon.uysotvoice.company.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Language} as its BCP-47 {@code code} ({@code "uz-UZ"}), not the enum
 * constant name ({@code "UZ_UZ"}) — the column already held that format before this
 * enum existed (report #6), so this keeps the stored value unchanged.
 */
@Converter
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public Language convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Language.fromCode(dbData);
    }
}
