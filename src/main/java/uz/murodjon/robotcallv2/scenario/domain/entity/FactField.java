package uz.murodjon.robotcallv2.scenario.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FactField(
        String name,
        String type,
        boolean required,
        String description
) {
    public FactField(String name, String type, boolean required) {
        this(name, type, required, null);
    }
}

