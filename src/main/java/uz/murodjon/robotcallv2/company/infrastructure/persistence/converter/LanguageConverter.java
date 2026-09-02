package uz.murodjon.robotcallv2.company.infrastructure.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import uz.murodjon.robotcallv2.company.domain.enums.Language;

@Converter(autoApply = true)
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language attribute) {
        return attribute != null ? attribute.code() : null;
    }

    @Override
    public Language convertToEntityAttribute(String dbData) {
        return dbData != null && !dbData.isBlank() ? Language.fromCode(dbData) : null;
    }
}
