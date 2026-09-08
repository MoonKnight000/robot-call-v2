package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolParamValueType;

import java.util.List;

public record ToolParamConfig(
        String name,
        String type,
        ToolParamValueType valueType,
        Object value,
        String description,
        List<String> allowedValues,
        boolean required
) {
    public ToolParamConfig {
        valueType = valueType == null ? ToolParamValueType.LLM_PROMPT : valueType;
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
