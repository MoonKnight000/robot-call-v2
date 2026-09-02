package uz.murodjon.robotcallv2.scenario.domain.entity;

public record ToolParamDef(
        String name,
        String type,
        boolean required,
        String constraint
) {
}
