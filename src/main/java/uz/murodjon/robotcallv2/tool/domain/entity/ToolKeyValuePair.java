package uz.murodjon.robotcallv2.tool.domain.entity;

public record ToolKeyValuePair(
        String key,
        String value,
        String type,
        Boolean redacted
) {
}
