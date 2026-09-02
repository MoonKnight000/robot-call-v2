package uz.murodjon.robotcallv2.shared.dialog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Tolerant {@link Sentiment} mapping: an unrecognized stored value (written by an older
 * build of the enum) reads back as {@code null} instead of failing the whole row load —
 * losing one field is better than losing the {@code call_result} it belongs to.
 */
@Converter
public class SentimentConverter implements AttributeConverter<Sentiment, String> {

    @Override
    public String convertToDatabaseColumn(Sentiment attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Sentiment convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return Sentiment.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
