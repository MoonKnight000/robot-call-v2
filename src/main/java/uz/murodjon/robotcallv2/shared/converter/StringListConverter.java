package uz.murodjon.robotcallv2.shared.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return String.join(",", attribute.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        return Arrays.stream(dbData.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
